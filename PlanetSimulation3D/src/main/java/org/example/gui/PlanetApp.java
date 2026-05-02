package org.example.gui;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
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

    private double mouseOldX, mouseOldY;
    private final Map<String, Sphere> nodes   = new HashMap<>();
    private final Map<String, Group>  systems = new HashMap<>();
    private boolean isPaused = false;

    @Override
    public void start(Stage stage) {
        // ── Dữ liệu ─────────────────────────────────────────
        PlanetDAO        dao        = new PlanetDAO();
        List<Planet>     planets    = dao.getAllPlanets();
        List<SpaceObject> satellites = dao.getAllSpaceObjects();

        // ── Tab 1: Mô phỏng 3D ───────────────────────────────
        SubScene sim3D = build3DScene(planets, satellites);
        StackPane simPane = new StackPane(sim3D);
        sim3D.widthProperty() .bind(simPane.widthProperty());
        sim3D.heightProperty().bind(simPane.heightProperty());

        Tab tab3D = new Tab("🪐  Mô Phỏng 3D", simPane);
        tab3D.setClosable(false);

        // ── Tab 2: Định Tuyến ─────────────────────────────────
        RoutingPanel routingPanel = new RoutingPanel();
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

            Group planetSys = new Group(sphere, label);
            nodes.put(p.getName(), sphere);
            systems.put(p.getName(), planetSys);
            universeGroup.getChildren().add(planetSys);

            new AnimationTimer() {
                @Override public void handle(long now) {
                    if (!isPaused) selfRot.setAngle(selfRot.getAngle() + 0.3);
                }
            }.start();
        }

        // ── Vệ tinh ───────────────────────────────────────────
        for (Planet p : planets) {
            boolean isSun       = p.getName().equals("Mặt Trời");
            Group   curSys      = systems.get(p.getName());
            Sphere  pSphere     = nodes.get(p.getName());

            for (SpaceObject s : satellites) {
                if (s.getPlanetId() != p.getId()) continue;

                Group satModel  = createSatelliteModel(s);
                double orbitDist = pSphere.getRadius() + (s.getAltitude() / 300.0) + 50;
                curSys.getChildren().add(satModel);

                new AnimationTimer() {
                    double angle = s.getLongitude();
                    @Override public void handle(long now) {
                        if (!isPaused) {
                            angle += s.getOrbitSpeed() / 45.0;
                            satModel.setTranslateX(orbitDist * Math.cos(Math.toRadians(angle)));
                            satModel.setTranslateZ(orbitDist * Math.sin(Math.toRadians(angle)));
                            satModel.setRotate(satModel.getRotate() + 0.5);
                        }
                    }
                }.start();
            }

            // Quỹ đạo hành tinh quanh Mặt Trời
            new AnimationTimer() {
                double angle      = Math.random() * 360;
                double orbitSpeed = isSun ? 0 : 0.04 / Math.sqrt(p.getDistanceFromSun() + 1);
                double r          = isSun ? 0 : 1500 + Math.log(p.getDistanceFromSun() + 1) * 900;

                @Override public void handle(long now) {
                    if (!isPaused && !isSun) {
                        angle += orbitSpeed;
                        double x, z;
                        if (p.getName().equals("Mặt Trăng") && systems.containsKey("Trái Đất")) {
                            Group earth = systems.get("Trái Đất");
                            x = earth.getTranslateX() + 350 * Math.cos(angle * 4);
                            z = earth.getTranslateZ() + 350 * Math.sin(angle * 4);
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
        camera.setTranslateZ(-28000);

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
        sub.setOnScroll(se -> camera.setTranslateZ(camera.getTranslateZ() + se.getDeltaY() * 70));

        return sub;
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

        return new Group(body, left, right, name);
    }

    public static void main(String[] args) { launch(args); }
}