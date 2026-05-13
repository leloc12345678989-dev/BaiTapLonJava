package org.example.gui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.StringConverter;
import org.example.dal.GroundStationDAO;
import org.example.dal.PlanetDAO;
import org.example.dal.RoutingDAO;
import org.example.dal.SpaceObjectDAO;
import org.example.model.*;
import org.example.service.PhysicsService;
import org.example.service.RoutingService;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel định tuyến vệ tinh – nhúng vào PlanetApp dưới dạng Tab.
 *
 * Tab 1 – Định Tuyến   : chọn hành tinh, trạm A/B → tìm route → vẽ bản đồ
 * Tab 2 – Trạm Mặt Đất : thêm / xóa / xem ground station
 * Tab 3 – Lịch Sử      : xem RoutingHistory từ DB
 */
public class RoutingPanel {

    // ── DAO & Service ─────────────────────────────────────────
    private final PlanetDAO        planetDAO  = new PlanetDAO();
    private final GroundStationDAO gsDAO      = new GroundStationDAO();
    private final RoutingDAO       routingDAO = new RoutingDAO();
    private final SpaceObjectDAO   objectDAO  = new SpaceObjectDAO();
    private final RoutingService   svc        = new RoutingService();

    // ── Dữ liệu ──────────────────────────────────────────────
    private List<Planet>        planets    = new ArrayList<>();
    private List<SpaceObject>   satellites = new ArrayList<>();

    // ── Controls (Tab 1) ──────────────────────────────────────
    private ComboBox<Planet>        cbPlanet;
    private ComboBox<GroundStation> cbA, cbB;
    private Label                   lblStatus, lblPath, lblStats;
    private Canvas                  canvas;

    // ── Controls (Tab 2) ──────────────────────────────────────
    private TableView<GroundStation> tblStations;
    private ComboBox<Planet>         cbPlanetGs;
    private TextField                tfName, tfLat, tfLon, tfDesc;

    // ── Controls (Tab 3) ──────────────────────────────────────
    private TableView<String[]> tblHistory;

    private TableView<SpaceObject> tblObjects;
    private ComboBox<Planet>       cbObjectPlanet;
    private ComboBox<String>       cbObjectType;
    private TextField              tfObjectName, tfObjectLat, tfObjectLon, tfObjectAlt, tfObjectTexture;
    private Label                  lblObjectSpeed;

    // ─────────────────────────────────────────────────────────
    public BorderPane build() {
        loadData();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:#0d1117;");

        // Header
        Label hdr = new Label("🛰  SATELLITE ROUTING SYSTEM");
        hdr.setStyle("-fx-background-color:#161b22;-fx-padding:14 20;"
                + "-fx-font-family:'Courier New';-fx-font-size:20;"
                + "-fx-font-weight:bold;-fx-text-fill:#00ff88;");
        hdr.setMaxWidth(Double.MAX_VALUE);
        root.setTop(hdr);

        TabPane tabs = new TabPane();
        tabs.setStyle("-fx-background-color:#0d1117;");

        Tab t1 = tab("📡  Định Tuyến",    buildTab1());
        Tab t2 = tab("🗺  Trạm Mặt Đất",  buildTab2());
        Tab t3 = tab("📜  Lịch Sử",       buildTab3());

        t3.setOnSelectionChanged(e -> { if (t3.isSelected()) refreshHistory(); });

        Tab objectTab = tab("Objects", buildTabObjects());
        objectTab.setOnSelectionChanged(e -> { if (objectTab.isSelected()) reloadObjectTable(); });

        tabs.getTabs().addAll(t1, t2, objectTab, t3);
        root.setCenter(tabs);
        return root;
    }

    // =========================================================
    //  TAB 1 – ĐỊNH TUYẾN
    // =========================================================
    private BorderPane buildTab1() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color:#0d1117;");
        pane.setPadding(new Insets(14));

        // ── Left form ────────────────────────────────────────
        VBox form = new VBox(10);
        form.setPrefWidth(310);
        form.setStyle("-fx-background-color:#161b22;-fx-background-radius:8;-fx-padding:16;");

        cbPlanet = combo();
        planets.forEach(p -> cbPlanet.getItems().add(p));
        StringConverter<Planet> planetConv = planetConverter();
        cbPlanet.setConverter(planetConv);
        applyWhiteComboCells(cbPlanet, planetConv);
        cbPlanet.setOnAction(e -> onPlanetChanged());

        // Default routing planet = Earth (Trái Đất).
        Planet earth = planets.stream()
                .filter(p -> p.getName() != null)
                .filter(p -> {
                    String n = p.getName().trim();
                    return n.equalsIgnoreCase("Trái Đất")
                            || n.equalsIgnoreCase("Trai Dat")
                            || n.equalsIgnoreCase("Earth");
                })
                .findFirst()
                .orElse(null);
        if (earth != null) {
            cbPlanet.setValue(earth);
            cbPlanet.setDisable(true);
        }
        cbPlanet.setPromptText("Chọn hành tinh...");

        cbA = stationCombo("Trạm nguồn A...");
        cbB = stationCombo("Trạm đích B...");

        Button btnFind = btn("🔍  TÌM ĐƯỜNG ĐI", "#00ff88");
        btnFind.setOnAction(e -> onFindRoute());

        lblStatus = lbl("Chưa chạy định tuyến.", "#8b949e");
        lblStatus.setWrapText(true);
        lblPath   = lbl("", "#58a6ff"); lblPath.setWrapText(true);
        lblStats  = lbl("", "#f0883e");

        form.getChildren().addAll(
                hdr2("HÀNH TINH"),      cbPlanet,
                hdr2("TRẠM NGUỒN A"),  cbA,
                hdr2("TRẠM ĐÍCH B"),   cbB,
                btnFind,
                sep(),
                hdr2("KẾT QUẢ"),       lblStatus,
                lblPath, sep(), lblStats
        );

        // ── Right canvas ─────────────────────────────────────
        canvas = new Canvas(660, 480);
        drawIdle();
        StackPane wrap = new StackPane(canvas);
        wrap.setStyle("-fx-background-color:#010409;-fx-background-radius:8;");

        pane.setLeft(form);
        BorderPane.setMargin(wrap, new Insets(0, 0, 0, 14));
        pane.setCenter(wrap);

        // Ensure station combos are populated for the default (Earth) planet selection.
        Platform.runLater(this::onPlanetChanged);
        return pane;
    }

    // =========================================================
    //  TAB 2 – TRẠM MẶT ĐẤT
    // =========================================================
    private BorderPane buildTab2() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color:#0d1117;");
        pane.setPadding(new Insets(14));

        // Table
        tblStations = new TableView<>();
        tblStations.setStyle("-fx-background-color:#161b22;-fx-text-fill:#c9d1d9;");
        tblStations.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblStations.getColumns().addAll(
                strCol("Tên Trạm",    gs -> gs.getStationName()),
                strCol("Planet ID",   gs -> String.valueOf(gs.getPlanetId())),
                strCol("Vĩ Độ",      gs -> String.format("%.3f°", gs.getLatitude())),
                strCol("Kinh Độ",    gs -> String.format("%.3f°", gs.getLongitude())),
                strCol("Mô Tả",      gs -> gs.getDescription())
        );
        reloadStationTable();

        // Form thêm
        VBox form = new VBox(10);
        form.setPrefWidth(290);
        form.setStyle("-fx-background-color:#161b22;-fx-background-radius:8;-fx-padding:16;");

        cbPlanetGs = combo();
        planets.forEach(p -> cbPlanetGs.getItems().add(p));
        StringConverter<Planet> planetConvGs = planetConverter();
        cbPlanetGs.setConverter(planetConvGs);
        applyWhiteComboCells(cbPlanetGs, planetConvGs);
        cbPlanetGs.setPromptText("Chọn hành tinh...");

        tfName = tf("Tên trạm");
        tfLat  = tf("Vĩ độ  (−90 → 90)");
        tfLon  = tf("Kinh độ (−180 → 180)");
        tfDesc = tf("Mô tả");

        Button btnAdd = btn("➕  THÊM", "#00ff88");
        Button btnDel = btn("🗑  XÓA",  "#ff6b6b");

        btnAdd.setOnAction(e -> {
            Planet sel = cbPlanetGs.getValue();
            if (sel == null || tfName.getText().isBlank()) {
                alert("Chọn hành tinh và nhập tên trạm!"); return;
            }
            try {
                GroundStation gs = new GroundStation(0,
                        tfName.getText().trim(), sel.getId(),
                        Double.parseDouble(tfLat.getText().trim()),
                        Double.parseDouble(tfLon.getText().trim()),
                        tfDesc.getText().trim());
                if (gsDAO.insert(gs) > 0) {
                    loadData(); reloadStationTable(); refreshRouteCombos();
                    alert("✅ Đã thêm: " + gs.getStationName());
                }
            } catch (NumberFormatException ex) { alert("Vĩ/Kinh độ phải là số!"); }
        });

        btnDel.setOnAction(e -> {
            GroundStation sel = tblStations.getSelectionModel().getSelectedItem();
            if (sel == null) { alert("Chọn dòng cần xóa!"); return; }
            if (gsDAO.delete(sel.getStationId())) {
                loadData(); reloadStationTable(); refreshRouteCombos();
                alert("✅ Đã xóa: " + sel.getStationName());
            }
        });

        form.getChildren().addAll(
                hdr2("THÊM TRẠM MỚI"),
                hdr2("Hành Tinh"), cbPlanetGs,
                hdr2("Tên"),       tfName,
                hdr2("Vĩ Độ"),     tfLat,
                hdr2("Kinh Độ"),   tfLon,
                hdr2("Mô Tả"),     tfDesc,
                btnAdd, btnDel
        );

        pane.setCenter(tblStations);
        BorderPane.setMargin(form, new Insets(0, 0, 0, 14));
        pane.setRight(form);
        return pane;
    }

    // =========================================================
    //  TAB 3 – LỊCH SỬ
    // =========================================================
    private BorderPane buildTabObjects() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color:#0d1117;");
        pane.setPadding(new Insets(14));

        tblObjects = new TableView<>();
        tblObjects.setStyle("-fx-background-color:#161b22;-fx-text-fill:#c9d1d9;");
        tblObjects.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblObjects.getColumns().addAll(
                strCol("Name", SpaceObject::getObjectName),
                strCol("Planet ID", o -> String.valueOf(o.getPlanetId())),
                strCol("Type", SpaceObject::getObjectType),
                strCol("Lat", o -> String.format("%.3f", o.getLatitude())),
                strCol("Lon", o -> String.format("%.3f", o.getLongitude())),
                strCol("Alt km", o -> String.format("%.1f", o.getAltitude())),
                strCol("Speed km/s", o -> String.format("%.4f", o.getOrbitSpeed()))
        );
        tblObjects.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> fillObjectForm(selected));

        cbObjectPlanet = combo();
        planets.forEach(p -> cbObjectPlanet.getItems().add(p));
        StringConverter<Planet> planetConv = planetConverter();
        cbObjectPlanet.setConverter(planetConv);
        applyWhiteComboCells(cbObjectPlanet, planetConv);
        cbObjectPlanet.setPromptText("Planet...");
        cbObjectPlanet.setOnAction(e -> updateObjectSpeedPreview());

        cbObjectType = combo();
        cbObjectType.getItems().addAll(
                "Communication Satellite",
                "Satellite",
                "Space Station",
                "Scientific Probe"
        );
        cbObjectType.setValue("Communication Satellite");
        applyWhiteComboCells(cbObjectType, new StringConverter<>() {
            public String toString(String value) { return value == null ? "" : value; }
            public String fromString(String value) { return value; }
        });

        tfObjectName = tf("Object name");
        tfObjectLat = tf("Latitude (-90..90)");
        tfObjectLon = tf("Longitude (-180..180)");
        tfObjectAlt = tf("Altitude km");
        tfObjectTexture = tf("Texture URL");
        lblObjectSpeed = lbl("OrbitSpeed: -- km/s", "#58a6ff");
        tfObjectAlt.textProperty().addListener((obs, old, value) -> updateObjectSpeedPreview());

        Button btnAdd = btn("ADD", "#00ff88");
        Button btnUpdate = btn("UPDATE", "#58a6ff");
        Button btnDelete = btn("DELETE", "#ff6b6b");
        Button btnClear = btn("CLEAR", "#8b949e");
        btnAdd.setOnAction(e -> onAddObject());
        btnUpdate.setOnAction(e -> onUpdateObject());
        btnDelete.setOnAction(e -> onDeleteObject());
        btnClear.setOnAction(e -> clearObjectForm());

        HBox row1 = new HBox(8, btnAdd, btnUpdate);
        HBox row2 = new HBox(8, btnDelete, btnClear);
        HBox.setHgrow(btnAdd, Priority.ALWAYS);
        HBox.setHgrow(btnUpdate, Priority.ALWAYS);
        HBox.setHgrow(btnDelete, Priority.ALWAYS);
        HBox.setHgrow(btnClear, Priority.ALWAYS);

        VBox form = new VBox(10);
        form.setPrefWidth(320);
        form.setStyle("-fx-background-color:#161b22;-fx-background-radius:8;-fx-padding:16;");
        form.getChildren().addAll(
                hdr2("SPACE OBJECT"),
                hdr2("Planet"), cbObjectPlanet,
                hdr2("Type"), cbObjectType,
                hdr2("Name"), tfObjectName,
                hdr2("Latitude"), tfObjectLat,
                hdr2("Longitude"), tfObjectLon,
                hdr2("Altitude"), tfObjectAlt,
                lblObjectSpeed,
                hdr2("Texture"), tfObjectTexture,
                row1, row2
        );

        pane.setCenter(tblObjects);
        BorderPane.setMargin(form, new Insets(0, 0, 0, 14));
        pane.setRight(form);

        reloadObjectTable();
        clearObjectForm();
        return pane;
    }

    private BorderPane buildTab3() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color:#0d1117;");
        pane.setPadding(new Insets(14));

        tblHistory = new TableView<>();
        tblHistory.setStyle("-fx-background-color:#161b22;");
        tblHistory.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] headers = {"ID","Nguồn A","Đích B","Hops","Dist (km)","Trạng Thái","Thời Gian"};
        for (int i = 0; i < headers.length; i++) {
            final int idx = i;
            TableColumn<String[], String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(d ->
                    new SimpleStringProperty(d.getValue()[idx]));
            // Tô màu cột trạng thái
            if (i == 5) col.setCellFactory(c -> new TableCell<>() {
                @Override protected void updateItem(String v, boolean empty) {
                    super.updateItem(v, empty);
                    if (empty || v == null) { setText(null); setStyle(""); return; }
                    setText(v);
                    setStyle("-fx-text-fill:" +
                            (v.equals("SUCCESS") ? "#00ff88" : "#ff6b6b") + ";");
                }
            });
            tblHistory.getColumns().add(col);
        }

        TextArea detail = new TextArea();
        detail.setStyle("-fx-background-color:#161b22;-fx-text-fill:#00ff88;"
                + "-fx-font-family:'Courier New';-fx-font-size:12;");
        detail.setEditable(false);
        detail.setPrefHeight(110);
        detail.setPromptText("Chọn một dòng để xem đường đi chi tiết...");

        tblHistory.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            if (row != null) detail.setText("Đường đi: " + row[7]);
        });

        Button btnRefresh = btn("🔄  Làm mới", "#58a6ff");
        btnRefresh.setOnAction(e -> refreshHistory());
        btnRefresh.setPrefWidth(140);

        VBox bottom = new VBox(8, hdr2("CHI TIẾT ĐƯỜNG ĐI"), detail, btnRefresh);
        bottom.setStyle("-fx-background-color:#161b22;-fx-background-radius:8;-fx-padding:12;");

        pane.setCenter(tblHistory);
        BorderPane.setMargin(bottom, new Insets(12, 0, 0, 0));
        pane.setBottom(bottom);
        return pane;
    }

    // =========================================================
    //  LOGIC
    // =========================================================
    private void onAddObject() {
        try {
            SpaceObject object = objectFromForm(0);
            int newId = objectDAO.insert(object);
            if (newId > 0) {
                refreshAfterObjectChange();
                clearObjectForm();
                alert("Saved object: " + object.getObjectName());
            } else {
                alert("Cannot save object.");
            }
        } catch (IllegalArgumentException ex) {
            alert(ex.getMessage());
        }
    }

    private void onUpdateObject() {
        SpaceObject selected = tblObjects.getSelectionModel().getSelectedItem();
        if (selected == null) {
            alert("Select an object to update.");
            return;
        }
        try {
            SpaceObject object = objectFromForm(selected.getObjectId());
            if (objectDAO.update(object)) {
                refreshAfterObjectChange();
                alert("Updated object: " + object.getObjectName());
            } else {
                alert("Cannot update object.");
            }
        } catch (IllegalArgumentException ex) {
            alert(ex.getMessage());
        }
    }

    private void onDeleteObject() {
        SpaceObject selected = tblObjects.getSelectionModel().getSelectedItem();
        if (selected == null) {
            alert("Select an object to delete.");
            return;
        }
        if (objectDAO.delete(selected.getObjectId())) {
            refreshAfterObjectChange();
            clearObjectForm();
            alert("Deleted object: " + selected.getObjectName());
        } else {
            alert("Cannot delete object.");
        }
    }

    private SpaceObject objectFromForm(int objectId) {
        Planet planet = cbObjectPlanet.getValue();
        if (planet == null) {
            throw new IllegalArgumentException("Select a planet.");
        }

        String name = requireText(tfObjectName, "Object name");
        double lat = parseDouble(tfObjectLat, "Latitude");
        double lon = parseDouble(tfObjectLon, "Longitude");
        double alt = parseDouble(tfObjectAlt, "Altitude");
        validateRange(lat, -90, 90, "Latitude");
        validateRange(lon, -180, 180, "Longitude");
        if (alt < 0) {
            throw new IllegalArgumentException("Altitude must be >= 0.");
        }

        String type = cbObjectType.getValue();
        if (type == null || type.isBlank()) {
            type = "Communication Satellite";
        }
        String texture = tfObjectTexture.getText() == null ? "" : tfObjectTexture.getText().trim();
        double speed = calculateObjectSpeed(planet, alt);

        return new SpaceObject(objectId, name, planet.getId(), lat, lon, alt, speed, type,
                texture.isBlank() ? null : texture);
    }

    private void fillObjectForm(SpaceObject object) {
        if (object == null || cbObjectPlanet == null) {
            return;
        }
        cbObjectPlanet.setValue(findPlanetById(object.getPlanetId()));
        if (object.getObjectType() != null && !cbObjectType.getItems().contains(object.getObjectType())) {
            cbObjectType.getItems().add(object.getObjectType());
        }
        cbObjectType.setValue(object.getObjectType() == null ? "Communication Satellite" : object.getObjectType());
        tfObjectName.setText(object.getObjectName());
        tfObjectLat.setText(String.valueOf(object.getLatitude()));
        tfObjectLon.setText(String.valueOf(object.getLongitude()));
        tfObjectAlt.setText(String.valueOf(object.getAltitude()));
        tfObjectTexture.setText(object.getTextureUrl() == null ? "" : object.getTextureUrl());
        updateObjectSpeedPreview();
    }

    private void clearObjectForm() {
        if (tblObjects != null) {
            tblObjects.getSelectionModel().clearSelection();
        }
        if (cbObjectPlanet != null) {
            cbObjectPlanet.setValue(planets.isEmpty() ? null : planets.get(0));
        }
        if (cbObjectType != null) {
            cbObjectType.setValue("Communication Satellite");
        }
        if (tfObjectName != null) {
            tfObjectName.clear();
            tfObjectLat.clear();
            tfObjectLon.clear();
            tfObjectAlt.clear();
            tfObjectTexture.clear();
        }
        updateObjectSpeedPreview();
    }

    private void updateObjectSpeedPreview() {
        if (lblObjectSpeed == null) {
            return;
        }
        Planet planet = cbObjectPlanet == null ? null : cbObjectPlanet.getValue();
        if (planet == null || tfObjectAlt == null || tfObjectAlt.getText().isBlank()) {
            lblObjectSpeed.setText("OrbitSpeed: -- km/s");
            return;
        }
        try {
            double alt = Double.parseDouble(tfObjectAlt.getText().trim());
            if (alt < 0) {
                lblObjectSpeed.setText("OrbitSpeed: invalid altitude");
                return;
            }
            lblObjectSpeed.setText(String.format("OrbitSpeed: %.4f km/s", calculateObjectSpeed(planet, alt)));
        } catch (NumberFormatException ex) {
            lblObjectSpeed.setText("OrbitSpeed: invalid altitude");
        }
    }

    private void refreshAfterObjectChange() {
        loadData();
        reloadObjectTable();
        refreshRouteCombos();
    }

    private Planet findPlanetById(int planetId) {
        return planets.stream()
                .filter(p -> p.getId() == planetId)
                .findFirst()
                .orElse(null);
    }

    private double calculateObjectSpeed(Planet planet, double altitudeKm) {
        return PhysicsService.calculateVelocity(planet.getMass(), planet.getRadius(), altitudeKm);
    }

    private boolean isRoutingObject(SpaceObject object) {
        String type = object.getObjectType();
        if (type == null || type.isBlank()) {
            return true;
        }
        String normalized = Normalizer.normalize(type, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        return normalized.contains("communication")
                || normalized.contains("satellite")
                || normalized.contains("lien lac")
                || normalized.contains("ve tinh");
    }

    private String requireText(TextField field, String label) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    private double parseDouble(TextField field, String label) {
        try {
            return Double.parseDouble(requireText(field, label));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    private void validateRange(double value, double min, double max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be from " + min + " to " + max + ".");
        }
    }

    private void onPlanetChanged() {
        Planet sel = cbPlanet.getValue();
        if (sel == null) return;
        if (cbA == null || cbB == null) return;
        cbA.getItems().clear(); cbB.getItems().clear();
        gsDAO.getByPlanet(sel.getId()).forEach(gs -> { cbA.getItems().add(gs); cbB.getItems().add(gs); });
        satellites = planetDAO.getAllSpaceObjects().stream()
                .filter(s -> s.getPlanetId() == sel.getId())
                .filter(this::isRoutingObject)
                .toList();
        drawIdle();
    }

    private void onFindRoute() {
        Planet       planet = cbPlanet.getValue();
        GroundStation a     = cbA.getValue();
        GroundStation b     = cbB.getValue();

        if (planet == null || a == null || b == null) {
            setStatus("⚠️ Chọn đầy đủ hành tinh và 2 trạm.", "#f0883e"); return;
        }
        if (a.getStationId() == b.getStationId()) {
            setStatus("⚠️ Trạm nguồn và đích không được trùng.", "#f0883e"); return;
        }

        setStatus("⏳ Đang tính toán...", "#8b949e");
        lblPath.setText(""); lblStats.setText("");

        new Thread(() -> {
            // Save history after each routing attempt.
            RouteResult r = svc.findRoute(planet, a, b, satellites, true);
            Platform.runLater(() -> {
                if (r.isSuccess()) {
                    setStatus("✅ Định tuyến thành công!", "#00ff88");
                    lblPath.setText(r.getPathString());
                    lblStats.setText(String.format("📏 %.1f km  |  🔁 %d hop(s)",
                            r.getTotalDistKm(), r.getHopCount()));
                } else {
                    setStatus("❌ " + r.getMessage(), "#ff6b6b");
                }
                drawRoute(planet, a, b, r);
            });
        }).start();
    }

    // =========================================================
    //  CANVAS – BẢN ĐỒ ĐỊNH TUYẾN
    // =========================================================
    private void drawIdle() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double W = canvas.getWidth(), H = canvas.getHeight();
        gc.setFill(Color.web("#010409")); gc.fillRect(0,0,W,H);
        drawGrid(gc, W, H);
        gc.setFill(Color.web("#30363d"));
        gc.setFont(Font.font("Courier New", 13));
        gc.fillText("Chọn hành tinh và 2 trạm mặt đất để hiển thị định tuyến", 60, H/2);
    }

    private void drawRoute(Planet planet, GroundStation a, GroundStation b, RouteResult result) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double W = canvas.getWidth(), H = canvas.getHeight();
        gc.setFill(Color.web("#010409")); gc.fillRect(0,0,W,H);
        drawGrid(gc, W, H);

        // Hàm lon/lat → pixel
        java.util.function.BiFunction<Double,Double,double[]> px = (lon, lat) -> new double[]{
                W/2 + (lon/180.0)*(W/2-24),
                H/2 - (lat/ 90.0)*(H/2-24)
        };

        // Tất cả vệ tinh (xám)
        for (SpaceObject s : satellites) {
            double[] p = px.apply(s.getLongitude(), s.getLatitude());
            gc.setFill(Color.web("#484f58"));
            gc.fillOval(p[0]-5, p[1]-5, 10, 10);
            gc.setFill(Color.web("#6e7681"));
            gc.setFont(Font.font("Courier New", 10));
            gc.fillText(s.getObjectName(), p[0]+8, p[1]+4);
        }

        // Đường route
        if (result.isSuccess()) {
            List<String> ids = result.getPathIds();
            for (int i = 0; i < ids.size()-1; i++) {
                double[] p1 = nodePixel(ids.get(i),   a, b, px);
                double[] p2 = nodePixel(ids.get(i+1), a, b, px);
                gc.setStroke(Color.web("#00ff88", 0.9));
                gc.setLineWidth(2.0);
                gc.setLineDashes(7, 4);
                gc.strokeLine(p1[0],p1[1],p2[0],p2[1]);
            }
            gc.setLineDashes();

            // Nút trên route
            List<String> names = result.getPathNames();
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                double[] p = nodePixel(id, a, b, px);
                boolean isA  = id.equals("GS_"+a.getStationId());
                boolean isB  = id.equals("GS_"+b.getStationId());
                boolean isGS = id.startsWith("GS_");
                Color c = isA ? Color.web("#00ff88") : isB ? Color.web("#58a6ff")
                        : Color.web("#d2a8ff");
                double r = isGS ? 9 : 7;
                gc.setFill(c);
                gc.fillOval(p[0]-r, p[1]-r, r*2, r*2);
                gc.setStroke(Color.WHITE); gc.setLineWidth(1.5);
                gc.strokeOval(p[0]-r, p[1]-r, r*2, r*2);
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Courier New", FontWeight.BOLD, 10));
                String label = names.get(i).replaceAll("[📡🛰\\s]","");
                gc.fillText(label, p[0]+r+4, p[1]+4);
            }
        }

        // Luôn vẽ nổi bật 2 trạm A/B
        drawStation(gc, px.apply(a.getLongitude(),a.getLatitude()), a.getStationName(), "#00ff88","A");
        drawStation(gc, px.apply(b.getLongitude(),b.getLatitude()), b.getStationName(), "#58a6ff","B");

        // Legend
        gc.setFill(Color.web("#161b22",0.88));
        gc.fillRoundRect(10,10,215,90,8,8);
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,11));
        gc.setFill(Color.web("#00ff88")); gc.fillText("●  Trạm nguồn A",    20,30);
        gc.setFill(Color.web("#58a6ff")); gc.fillText("●  Trạm đích B",     20,48);
        gc.setFill(Color.web("#d2a8ff")); gc.fillText("●  Vệ tinh (route)", 20,66);
        gc.setFill(Color.web("#484f58")); gc.fillText("●  Vệ tinh khác",    20,84);
    }

    private void drawGrid(GraphicsContext gc, double W, double H) {
        gc.setStroke(Color.web("#21262d")); gc.setLineWidth(0.5);
        for (int i=0;i<=12;i++){double x=W*i/12; gc.strokeLine(x,0,x,H);}
        for (int i=0;i<= 6;i++){double y=H*i/6;  gc.strokeLine(0,y,W,y);}
        gc.setStroke(Color.web("#30363d")); gc.setLineWidth(1);
        gc.strokeLine(W/2,0,W/2,H); gc.strokeLine(0,H/2,W,H/2);
    }

    private void drawStation(GraphicsContext gc, double[] p,
                             String name, String hex, String tag) {
        gc.setFill(Color.web(hex));
        gc.fillOval(p[0]-9,p[1]-9,18,18);
        gc.setStroke(Color.WHITE); gc.setLineWidth(2);
        gc.strokeOval(p[0]-9,p[1]-9,18,18);
        gc.setFill(Color.web("#0d1117"));
        gc.setFont(Font.font("Courier New",FontWeight.BOLD,11));
        gc.fillText(tag, p[0]-4, p[1]+4);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Courier New",11));
        gc.fillText(name, p[0]+13, p[1]+4);
    }

    private double[] nodePixel(String id, GroundStation a, GroundStation b,
                               java.util.function.BiFunction<Double,Double,double[]> px) {
        if (id.equals("GS_"+a.getStationId())) return px.apply(a.getLongitude(),a.getLatitude());
        if (id.equals("GS_"+b.getStationId())) return px.apply(b.getLongitude(),b.getLatitude());
        for (SpaceObject s : satellites)
            if (("SAT_"+s.getObjectId()).equals(id))
                return px.apply(s.getLongitude(),s.getLatitude());
        return new double[]{0,0};
    }

    // =========================================================
    //  HELPERS
    // =========================================================
    private void loadData() {
        planets    = planetDAO.getAllPlanets();
        satellites = planetDAO.getAllSpaceObjects();
    }

    private void reloadStationTable() {
        if (tblStations == null) return;
        tblStations.getItems().clear();
        gsDAO.getAll().forEach(gs -> tblStations.getItems().add(gs));
    }

    private void reloadObjectTable() {
        if (tblObjects == null) return;
        tblObjects.getItems().clear();
        objectDAO.getAll().forEach(object -> tblObjects.getItems().add(object));
    }

    private void refreshHistory() {
        if (tblHistory == null) return;
        tblHistory.getItems().clear();
        routingDAO.getRecentHistory(50).forEach(row -> tblHistory.getItems().add(row));
    }

    private void refreshRouteCombos() {
        if (cbPlanet == null || cbPlanet.getValue() == null) return;
        onPlanetChanged();
    }

    private void setStatus(String msg, String color) {
        lblStatus.setText(msg);
        lblStatus.setStyle("-fx-text-fill:" + color + ";"
                + "-fx-font-family:'Courier New';-fx-font-size:13;");
    }

    // ── UI factory helpers ────────────────────────────────────
    private Tab tab(String title, javafx.scene.Node content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }

    private <T> ComboBox<T> combo() {
        ComboBox<T> cb = new ComboBox<>();
        // White text for readability; hide the arrow mark (user requested).
        cb.setStyle("-fx-background-color:#21262d;"
                + "-fx-background-radius:6;-fx-font-size:13;"
                + "-fx-mark-color: transparent;"
                + "-fx-prompt-text-fill:#6e7681;");
        cb.setMaxWidth(Double.MAX_VALUE);
        return cb;
    }

    private ComboBox<GroundStation> stationCombo(String prompt) {
        ComboBox<GroundStation> cb = combo();
        cb.setPromptText(prompt);
        StringConverter<GroundStation> conv = new StringConverter<>() {
            public String toString(GroundStation g)   { return g==null?"":g.getStationName(); }
            public GroundStation fromString(String s) { return null; }
        };
        cb.setConverter(conv);
        applyWhiteComboCells(cb, conv);
        return cb;
    }

    private StringConverter<Planet> planetConverter() {
        return new StringConverter<>() {
            public String toString(Planet p)   { return p==null?"":p.getName(); }
            public Planet fromString(String s) { return null; }
        };
    }

    private <T> void applyWhiteComboCells(ComboBox<T> cb, StringConverter<T> conv) {
        cb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(conv == null ? String.valueOf(item) : conv.toString(item));
                setTextFill(Color.WHITE);
            }
        });
        cb.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(conv == null ? String.valueOf(item) : conv.toString(item));
                setTextFill(Color.WHITE);
                setStyle("-fx-background-color:#161b22;");
            }
        });
    }

    private Button btn(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + color + ";-fx-text-fill:#0d1117;"
                + "-fx-font-weight:bold;-fx-background-radius:6;"
                + "-fx-padding:8 16;-fx-cursor:hand;-fx-font-size:13;");
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private Label lbl(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + color + ";-fx-font-family:'Courier New';-fx-font-size:13;");
        return l;
    }

    private Label hdr2(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#8b949e;-fx-font-family:'Courier New';"
                + "-fx-font-weight:bold;-fx-font-size:11;");
        return l;
    }

    private Separator sep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color:#30363d;");
        return s;
    }

    private TextField tf(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle("-fx-background-color:#21262d;-fx-text-fill:#c9d1d9;"
                + "-fx-background-radius:6;-fx-font-size:13;"
                + "-fx-prompt-text-fill:#484f58;");
        return tf;
    }

    @SuppressWarnings("unchecked")
    private <T> TableColumn<T,String> strCol(String title,
                                             java.util.function.Function<T,String> getter) {
        TableColumn<T,String> col = new TableColumn<>(title);
        col.setCellValueFactory(d -> new SimpleStringProperty(getter.apply(d.getValue())));
        return col;
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Thông báo"); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}
