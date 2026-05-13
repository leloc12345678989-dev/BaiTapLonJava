package org.example.gui;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import org.example.dal.PlanetDAO;
import org.example.model.Planet;
import org.example.model.SpaceObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PlanetApp – cửa sổ chính.
 *
 * Cấu trúc:
 *   TabPane
 *   ├── Tab "🪐 Mô Phỏng 3D"   → màn hình JavaFX 3D gốc
 *   └── Tab "🛰 Định Tuyến"     → RoutingPanel (mới)
 */
public class PlanetApp extends Application {

    private static final double MIN_CAMERA_Z = -500000.0;
    private static final double MAX_CAMERA_Z = -500.0;
    private static final double INITIAL_CAMERA_Z = -39000.0;
    private static final double MOON_ORBIT_RADIUS = 700.0;

    private double mouseOldX, mouseOldY;
    private final Map<String, Sphere> nodes   = new HashMap<>();
    private final Map<String, Group>  systems = new HashMap<>();
    private final Map<String, Group>  satelliteLayers = new HashMap<>();
    private final List<AnimationTimer> satelliteTimers = new ArrayList<>();
    private boolean isPaused = false;
    private VBox planetInfoPanel;
    private Label planetInfoTitle;
    private Label planetInfoRadius;
    private Label planetInfoMass;
    private Label planetInfoDistance;
    private Label planetInfoId;
    private final DecimalFormat numberFormat = new DecimalFormat("#,##0.###");
    private final DecimalFormat scientificFormat = new DecimalFormat("0.###E0");

    @Override
    public void start(Stage stage) {
        // ── Dữ liệu ─────────────────────────────────────────
        PlanetDAO        dao        = new PlanetDAO();
        List<Planet>     planets    = dao.getAllPlanets();
        List<SpaceObject> satellites = dao.getAllSpaceObjects();

        // ── Tab 1: Mô phỏng 3D ───────────────────────────────
        SubScene sim3D = build3DScene(planets, satellites);
        planetInfoPanel = createPlanetInfoPanel();
        StackPane simPane = new StackPane(sim3D, planetInfoPanel);
        StackPane.setAlignment(planetInfoPanel, Pos.TOP_RIGHT);
        StackPane.setMargin(planetInfoPanel, new Insets(8));
        sim3D.widthProperty() .bind(simPane.widthProperty());
        sim3D.heightProperty().bind(simPane.heightProperty());

        Tab tab3D = new Tab("🪐  Mô Phỏng 3D", simPane);
        tab3D.setClosable(false);

        // ── Tab 2: Định Tuyến ─────────────────────────────────
        RoutingPanel routingPanel = new RoutingPanel(this::refreshSatellitesIn3D);
        BorderPane   routingPane  = routingPanel.build();

        Tab tabRoute = new Tab("🛰  Định Tuyến Vệ Tinh", routingPane);
        tabRoute.setClosable(false);

        // ── TabPane chính ─────────────────────────────────────
        TabPane tabPane = new TabPane(tab3D, tabRoute);
        tabPane.setStyle("-fx-background-color:#000;");
        tabPane.setTabMinWidth(180);

        Scene scene = new Scene(tabPane, 1280, 760);
        scene.setFill(Color.BLACK);

        // SPACE để pause mô phỏng
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.SPACE) isPaused = !isPaused; });

        stage.setTitle("DUT Solar System PBL3 – Satellite Pro Edition");
        stage.setScene(scene);
        stage.show();
    }

    // =========================================================
    //  XÂY DỰNG SUBSCENE 3D
    // =========================================================
    private SubScene build3DScene(List<Planet> planets, List<SpaceObject> satellites) {
        nodes.clear();
        systems.clear();
        satelliteLayers.clear();
        satelliteTimers.clear();

        Group universeGroup = new Group();
        PointLight sunLight  = new PointLight(Color.WHITE);
        sunLight.setTranslateZ(-1500);
        AmbientLight ambient = new AmbientLight(Color.rgb(120, 120, 120));

        // ── Hành tinh ─────────────────────────────────────────
        for (Planet p : planets) {
            boolean isSun        = p.getName().equals("Mặt Trời");
            double  scaledRadius = isSun ? 320 : Math.pow(p.getRadius(), 0.4) * 4 + 5;

            Sphere sphere = new Sphere(scaledRadius);
            PhongMaterial mat = new PhongMaterial();
            try {
                Image tex = new Image(p.getTextureUrl(), true);
                mat.setDiffuseMap(tex);
                if (isSun) mat.setSelfIlluminationMap(tex);
            } catch (Exception ex) {
                mat.setDiffuseColor(isSun ? Color.YELLOW : Color.ORANGE);
            }
            sphere.setMaterial(mat);

            Rotate selfRot = new Rotate(0, Rotate.Y_AXIS);
            sphere.getTransforms().add(selfRot);

            Text label = new Text(p.getName());
            label.setFill(Color.WHITE);
            label.setFont(Font.font("Arial", 18));
            label.setTranslateY(-(scaledRadius + 35));

            Group satelliteLayer = new Group();
            Group planetSys = new Group(sphere, label, satelliteLayer);
            sphere.setCursor(Cursor.HAND);
            label.setCursor(Cursor.HAND);
            sphere.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    showPlanetInfo(p);
                    event.consume();
                }
            });
            label.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    showPlanetInfo(p);
                    event.consume();
                }
            });
            nodes.put(p.getName(), sphere);
            systems.put(p.getName(), planetSys);
            satelliteLayers.put(p.getName(), satelliteLayer);
            universeGroup.getChildren().add(planetSys);

            new AnimationTimer() {
                @Override public void handle(long now) {
                    if (!isPaused) selfRot.setAngle(selfRot.getAngle() + 0.3);
                }
            }.start();
        }

        // ── Vệ tinh ───────────────────────────────────────────
        addSatellitesToScene(planets, satellites);

        for (Planet p : planets) {
            boolean isSun       = p.getName().equals("Mặt Trời");
            Group   curSys      = systems.get(p.getName());

            // Quỹ đạo hành tinh quanh Mặt Trời
            new AnimationTimer() {
                double angle      = initialOrbitAngle(p);
                double orbitSpeed = isSun ? 0 : 0.04 / Math.sqrt(p.getDistanceFromSun() + 1);
                double r          = planetOrbitRadius(p);

                @Override public void handle(long now) {
                    if (!isPaused && !isSun) {
                        angle += orbitSpeed;
                        double x, z;
                        if (p.getName().equals("Mặt Trăng") && systems.containsKey("Trái Đất")) {
                            Group earth = systems.get("Trái Đất");
                            x = earth.getTranslateX() + MOON_ORBIT_RADIUS * Math.cos(angle * 4);
                            z = earth.getTranslateZ() + MOON_ORBIT_RADIUS * Math.sin(angle * 4);
                        } else {
                            x = r * Math.cos(angle);
                            z = r * Math.sin(angle);
                        }
                        curSys.setTranslateX(x);
                        curSys.setTranslateZ(z);
                    }
                }
            }.start();
        }

        // ── Camera ────────────────────────────────────────────
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(500000.0);
        camera.setTranslateZ(INITIAL_CAMERA_Z);

        SubScene sub = new SubScene(
                new Group(universeGroup, sunLight, ambient),
                1280, 720, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.BLACK);
        sub.setCamera(camera);

        Rotate rotX = new Rotate(20, Rotate.X_AXIS);
        Rotate rotY = new Rotate(0,  Rotate.Y_AXIS);
        universeGroup.getTransforms().addAll(rotX, rotY);

        sub.setOnMousePressed (me -> { mouseOldX = me.getSceneX(); mouseOldY = me.getSceneY(); });
        sub.setOnMouseDragged (me -> {
            rotY.setAngle(rotY.getAngle() + (me.getSceneX() - mouseOldX) * 0.2);
            rotX.setAngle(rotX.getAngle() - (me.getSceneY() - mouseOldY) * 0.2);
            mouseOldX = me.getSceneX(); mouseOldY = me.getSceneY();
        });
        sub.setOnScroll(se -> zoomCameraToMouse(camera, sub, se));

        return sub;
    }

    private void zoomCameraToMouse(PerspectiveCamera camera, SubScene sub, ScrollEvent event) {
        double oldZ = camera.getTranslateZ();
        double newZ = clamp(oldZ + event.getDeltaY() * 70, MIN_CAMERA_Z, MAX_CAMERA_Z);
        if (newZ == oldZ) {
            return;
        }

        double mouseX = event.getX();
        double mouseY = event.getY();
        double[] before = pointOnFocusPlane(camera, sub, mouseX, mouseY, -oldZ);
        double[] after = pointOnFocusPlane(camera, sub, mouseX, mouseY, -newZ);

        camera.setTranslateX(camera.getTranslateX() + before[0] - after[0]);
        camera.setTranslateY(camera.getTranslateY() + before[1] - after[1]);
        camera.setTranslateZ(newZ);
        event.consume();
    }

    private double[] pointOnFocusPlane(PerspectiveCamera camera, SubScene sub,
                                       double mouseX, double mouseY, double distance) {
        double width = Math.max(1, sub.getWidth());
        double height = Math.max(1, sub.getHeight());
        double aspect = width / height;
        double halfField = Math.tan(Math.toRadians(camera.getFieldOfView()) / 2.0);

        double halfWidth;
        double halfHeight;
        if (camera.isVerticalFieldOfView()) {
            halfHeight = distance * halfField;
            halfWidth = halfHeight * aspect;
        } else {
            halfWidth = distance * halfField;
            halfHeight = halfWidth / aspect;
        }

        double normalizedX = (mouseX / width) * 2.0 - 1.0;
        double normalizedY = (mouseY / height) * 2.0 - 1.0;
        return new double[]{
                camera.getTranslateX() + normalizedX * halfWidth,
                camera.getTranslateY() + normalizedY * halfHeight
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void refreshSatellitesIn3D() {
        PlanetDAO dao = new PlanetDAO();
        addSatellitesToScene(dao.getAllPlanets(), dao.getAllSpaceObjects());
    }

    private void addSatellitesToScene(List<Planet> planets, List<SpaceObject> satellites) {
        clearSatellitesFromScene();

        for (Planet p : planets) {
            Group satelliteLayer = satelliteLayers.get(p.getName());
            Sphere pSphere = nodes.get(p.getName());
            if (satelliteLayer == null || pSphere == null) {
                continue;
            }

            for (SpaceObject s : satellites) {
                if (s.getPlanetId() != p.getId()) continue;

                Group satModel = createSatelliteModel(s);
                double orbitDist = pSphere.getRadius() + (s.getAltitude() / 300.0) + 50;
                placeSatelliteOnOrbit(satModel, orbitDist, s.getLatitude(), s.getLongitude());
                satelliteLayer.getChildren().add(satModel);

                AnimationTimer timer = new AnimationTimer() {
                    double angle = s.getLongitude();
                    @Override public void handle(long now) {
                        if (!isPaused) {
                            angle += s.getOrbitSpeed() / 45.0;
                            placeSatelliteOnOrbit(satModel, orbitDist, s.getLatitude(), angle);
                            satModel.setRotate(satModel.getRotate() + 0.5);
                        }
                    }
                };
                timer.start();
                satelliteTimers.add(timer);
            }
        }
    }

    private void clearSatellitesFromScene() {
        satelliteTimers.forEach(AnimationTimer::stop);
        satelliteTimers.clear();
        satelliteLayers.values().forEach(layer -> layer.getChildren().clear());
    }

    private double planetOrbitRadius(Planet planet) {
        return switch (planet.getName()) {
            case "Mặt Trời" -> 0;
            case "Sao Thủy" -> 1800;
            case "Sao Kim" -> 3000;
            case "Trái Đất" -> 4500;
            case "Mặt Trăng" -> 0;
            case "Sao Hỏa" -> 6000;
            case "Sao Mộc" -> 8200;
            case "Sao Thổ" -> 10800;
            case "Sao Thiên Vương" -> 13400;
            case "Sao Hải Vương" -> 16000;
            default -> 900 + Math.sqrt(Math.max(0, planet.getDistanceFromSun())) * 210;
        };
    }

    private double initialOrbitAngle(Planet planet) {
        return switch (planet.getName()) {
            case "Sao Thủy" -> 210;
            case "Sao Kim" -> 145;
            case "Trái Đất" -> 95;
            case "Mặt Trăng" -> 95;
            case "Sao Hỏa" -> 35;
            case "Sao Mộc" -> 315;
            case "Sao Thổ" -> 255;
            case "Sao Thiên Vương" -> 20;
            case "Sao Hải Vương" -> 285;
            default -> 0;
        };
    }

    private void placeSatelliteOnOrbit(Group satModel, double orbitRadius,
                                       double inclinationDeg, double phaseDeg) {
        double phase = Math.toRadians(phaseDeg);
        double inclination = Math.toRadians(inclinationDeg);

        satModel.setTranslateX(orbitRadius * Math.cos(phase));
        satModel.setTranslateY(orbitRadius * Math.sin(phase) * Math.sin(inclination));
        satModel.setTranslateZ(orbitRadius * Math.sin(phase) * Math.cos(inclination));
    }

    private VBox createPlanetInfoPanel() {
        planetInfoTitle = new Label();
        planetInfoTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");

        Button closeButton = new Button("X");
        closeButton.setFocusTraversable(false);
        closeButton.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #d1d5db;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 0 6 0 6;"
        );
        closeButton.setOnAction(event -> planetInfoPanel.setVisible(false));

        HBox header = new HBox(6, planetInfoTitle, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);

        planetInfoId = createInfoLabel();
        planetInfoRadius = createInfoLabel();
        planetInfoMass = createInfoLabel();
        planetInfoDistance = createInfoLabel();

        VBox panel = new VBox(3, header, planetInfoId, planetInfoRadius, planetInfoMass, planetInfoDistance);
        panel.setMaxWidth(260);
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        panel.setPadding(new Insets(7, 9, 7, 9));
        panel.setStyle(
                "-fx-background-color: rgba(9, 14, 24, 0.82);" +
                "-fx-background-radius: 6;" +
                "-fx-border-color: rgba(255,255,255,0.16);" +
                "-fx-border-radius: 6;"
        );
        panel.setVisible(false);
        return panel;
    }

    private Label createInfoLabel() {
        Label label = new Label();
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 11px; -fx-text-fill: #e5e7eb;");
        return label;
    }

    private void showPlanetInfo(Planet planet) {
        planetInfoTitle.setText(planet.getName());
        planetInfoId.setText("Ma: " + planet.getId());
        planetInfoRadius.setText("BK: " + numberFormat.format(planet.getRadius()) + " km");
        planetInfoMass.setText("KL: " + scientificFormat.format(planet.getMass()) + " kg");
        planetInfoDistance.setText("Cach MT: "
                + numberFormat.format(planet.getDistanceFromSun()) + " trieu km");
        planetInfoPanel.setVisible(true);
    }

    // =========================================================
    //  MODEL VỆ TINH
    // =========================================================
    private Group createSatelliteModel(SpaceObject s) {
        Box body = new Box(12, 12, 12);
        PhongMaterial bodyMat = new PhongMaterial(Color.SILVER);
        bodyMat.setSpecularColor(Color.WHITE);
        body.setMaterial(bodyMat);

        Box left  = new Box(35, 10, 1);
        Box right = new Box(35, 10, 1);
        left.setTranslateX(-25); right.setTranslateX(25);

        PhongMaterial wingMat = new PhongMaterial();
        try {
            if (s.getTextureUrl() != null)
                wingMat.setDiffuseMap(new Image(s.getTextureUrl(), true));
            else wingMat.setDiffuseColor(Color.DARKBLUE);
        } catch (Exception ex) { wingMat.setDiffuseColor(Color.DARKBLUE); }
        left.setMaterial(wingMat); right.setMaterial(wingMat);

        Text name = new Text(s.getObjectName());
        name.setFill(Color.GOLD);
        name.setFont(Font.font("Arial Bold", 13));
        name.setTranslateY(-20);

        Text speed = new Text(String.format("%.4f km/s", s.getOrbitSpeed()));
        speed.setFill(Color.web("#00ff88"));
        speed.setFont(Font.font("Courier New", 10));
        speed.setTranslateY(-8);

        return new Group(body, left, right, name, speed);
    }

    public static void main(String[] args) { launch(args); }
}
