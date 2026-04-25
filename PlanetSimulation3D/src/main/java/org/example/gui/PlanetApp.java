package org.example.gui;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
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

public class PlanetApp extends Application {
    private double mouseOldX, mouseOldY;
    private final Map<String, Sphere> nodes = new HashMap<>();
    private final Map<String, Group> systems = new HashMap<>();
    private boolean isPaused = false;

    @Override
    public void start(Stage stage) {
        PlanetDAO dao = new PlanetDAO();
        List<Planet> planets = dao.getAllPlanets();
        List<SpaceObject> satellites = dao.getAllSpaceObjects();

        Group universeGroup = new Group();
        PointLight sunLight = new PointLight(Color.WHITE);
        sunLight.setTranslateZ(-1500);
        AmbientLight ambientLight = new AmbientLight(Color.rgb(120, 120, 120));

        // BƯỚC 1: KHỞI TẠO CÁC HÀNH TINH
        for (Planet p : planets) {
            boolean isSun = p.getName().equals("Mặt Trời");
            double scaledRadius = isSun ? 320 : Math.pow(p.getRadius(), 0.4) * 4 + 5;

            Sphere sphere = new Sphere(scaledRadius);
            PhongMaterial material = new PhongMaterial();
            try {
                material.setDiffuseMap(new Image(p.getTextureUrl(), true));
                if (isSun) material.setSelfIlluminationMap(new Image(p.getTextureUrl(), true));
            } catch (Exception e) {
                material.setDiffuseColor(isSun ? Color.YELLOW : Color.ORANGE);
            }
            sphere.setMaterial(material);

            Rotate selfRotate = new Rotate(0, Rotate.Y_AXIS);
            sphere.getTransforms().add(selfRotate);

            Text label = new Text(p.getName());
            label.setFill(Color.WHITE);
            label.setFont(Font.font("Arial", 18));
            label.setTranslateY(-(scaledRadius + 35));

            Group planetSystem = new Group(sphere, label);
            nodes.put(p.getName(), sphere);
            systems.put(p.getName(), planetSystem);
            universeGroup.getChildren().add(planetSystem);

            new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (!isPaused) selfRotate.setAngle(selfRotate.getAngle() + 0.3);
                }
            }.start();
        }

        // BƯỚC 2: THIẾT LẬP VỆ TINH CÓ CÁNH VÀ QUỸ ĐẠO
        for (Planet p : planets) {
            boolean isSun = p.getName().equals("Mặt Trời");
            Group currentSystem = systems.get(p.getName());
            Sphere pSphere = nodes.get(p.getName());

            for (SpaceObject s : satellites) {
                if (s.getPlanetId() == p.getId()) {
                    // TẠO MODEL VỆ TINH PHỨC HỢP
                    Group satModel = createSatelliteModel(s);

                    // Giãn cách quỹ đạo vệ tinh (Chia 300 để không quá gần bề mặt)
                    double orbitDist = pSphere.getRadius() + (s.getAltitude() / 300.0) + 50;
                    currentSystem.getChildren().add(satModel);

                    new AnimationTimer() {
                        double satAngle = s.getLongitude();
                        @Override
                        public void handle(long now) {
                            if (!isPaused) {
                                satAngle += (s.getOrbitSpeed() / 45.0);
                                double sx = orbitDist * Math.cos(Math.toRadians(satAngle));
                                double sz = orbitDist * Math.sin(Math.toRadians(satAngle));

                                satModel.setTranslateX(sx);
                                satModel.setTranslateZ(sz);

                                // Vệ tinh tự xoay nhẹ để nhìn thấy các góc cạnh
                                satModel.setRotate(satModel.getRotate() + 0.5);
                            }
                        }
                    }.start();
                }
            }

            // Animation Quỹ đạo hành tinh quanh Mặt Trời
            new AnimationTimer() {
                double angle = Math.random() * 360;
                double orbitSpeed = isSun ? 0 : 0.04 / Math.sqrt(p.getDistanceFromSun() + 1);
                double r = isSun ? 0 : 1500 + (Math.log(p.getDistanceFromSun() + 1) * 900);

                @Override
                public void handle(long now) {
                    if (!isPaused && !isSun) {
                        angle += orbitSpeed;
                        double x, z;
                        if (p.getName().equals("Mặt Trăng") && systems.containsKey("Trái Đất")) {
                            Group earthSystem = systems.get("Trái Đất");
                            x = earthSystem.getTranslateX() + 350 * Math.cos(angle * 4);
                            z = earthSystem.getTranslateZ() + 350 * Math.sin(angle * 4);
                        } else {
                            x = r * Math.cos(angle);
                            z = r * Math.sin(angle);
                        }
                        currentSystem.setTranslateX(x);
                        currentSystem.setTranslateZ(z);
                    }
                }
            }.start();
        }

        // Camera & Scene setup
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1); camera.setFarClip(500000.0);
        camera.setTranslateZ(-28000);

        Scene scene = new Scene(new Group(universeGroup, sunLight, ambientLight), 1280, 720, true);
        scene.setFill(Color.BLACK);
        scene.setCamera(camera);

        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.SPACE) isPaused = !isPaused; });

        Rotate rotateX = new Rotate(20, Rotate.X_AXIS);
        Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
        universeGroup.getTransforms().addAll(rotateX, rotateY);

        scene.setOnMousePressed(me -> { mouseOldX = me.getSceneX(); mouseOldY = me.getSceneY(); });
        scene.setOnMouseDragged(me -> {
            rotateY.setAngle(rotateY.getAngle() + (me.getSceneX() - mouseOldX) * 0.2);
            rotateX.setAngle(rotateX.getAngle() - (me.getSceneY() - mouseOldY) * 0.2);
            mouseOldX = me.getSceneX(); mouseOldY = me.getSceneY();
        });
        scene.setOnScroll(se -> camera.setTranslateZ(camera.getTranslateZ() + se.getDeltaY() * 70));

        stage.setTitle("DUT Solar System PBL3 - Satellite Pro Edition");
        stage.setScene(scene);
        stage.show();
    }

    private Sphere createGroundStation(String name, Color color) {
        Sphere station = new Sphere(4);
        station.setMaterial(new PhongMaterial(color));
        // Giả sử đặt ở Đà Nẵng: Vĩ độ 16.0, Kinh độ 108.0
        // Ta cần một hàm chuyển đổi Lat/Lon sang X, Y, Z
        return station;
    }
    // HÀM TẠO MODEL VỆ TINH CÓ CÁNH
    private Group createSatelliteModel(SpaceObject s) {
        // 1. Thân vệ tinh (Box bạc)
        Box body = new Box(12, 12, 12);
        PhongMaterial bodyMat = new PhongMaterial(Color.SILVER);
        bodyMat.setSpecularColor(Color.WHITE);
        body.setMaterial(bodyMat);

        // 2. Cánh pin mặt trời (Box mỏng)
        Box leftWing = new Box(35, 10, 1);
        Box rightWing = new Box(35, 10, 1);
        leftWing.setTranslateX(-25);
        rightWing.setTranslateX(25);

        PhongMaterial wingMat = new PhongMaterial();
        try {
            // Load texture tấm pin mặt trời từ DB
            if (s.getTextureUrl() != null) {
                wingMat.setDiffuseMap(new Image(s.getTextureUrl(), true));
            } else {
                wingMat.setDiffuseColor(Color.DARKBLUE);
            }
        } catch (Exception e) {
            wingMat.setDiffuseColor(Color.DARKBLUE);
        }
        leftWing.setMaterial(wingMat);
        rightWing.setMaterial(wingMat);

        Text name = new Text(s.getObjectName());
        name.setFill(Color.GOLD);
        name.setFont(Font.font("Arial Bold", 13));
        name.setTranslateY(-20);

        return new Group(body, leftWing, rightWing, name);
    }

    public static void main(String[] args) { launch(args); }
}