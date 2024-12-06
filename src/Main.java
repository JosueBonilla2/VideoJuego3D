import com.sun.j3d.utils.geometry.Box;
import com.sun.j3d.utils.geometry.Cylinder;
import com.sun.j3d.utils.geometry.Sphere;
import com.sun.j3d.utils.image.TextureLoader;
import com.sun.j3d.utils.universe.SimpleUniverse;
import javax.media.j3d.*;
import javax.sound.sampled.*;
import javax.sound.sampled.Clip;
import javax.swing.*;
import javax.vecmath.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Main {
    private static TransformGroup camaraTG;
    private static Transform3D camaraTransformacion = new Transform3D();
    private static Vector3f posicionCamara = new Vector3f(1.2f, -0.7f, 2.0f);
    private static float anguloX = 0.0f;
    private static float anguloY = 0.0f;
    private static Point lastMousePosition = new Point();
    private static final float SENSITIVITY = 0.002f;
    private static final float COLLISION_RADIUS = 0.3f;
    private static final float LIMITE_ESCENARIO = 5.0f;
    private static ArrayList<Box> edificios = new ArrayList<>();
    private static ArrayList<TransformGroup> enemigos = new ArrayList<>();
    private static Timer timer = new Timer();
    private static Canvas3D canvas;
    private static KeyAdapter keyAdapter;
    private static MouseMotionListener mouseMotionListener;
    private static SimpleUniverse universo;

    public static void main(String[] args) {
        JFrame ventana = new JFrame("Ciudad Nocturna 3D");
        ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ventana.setSize(800, 800);
        ventana.setLayout(new BorderLayout());

        canvas = new Canvas3D(SimpleUniverse.getPreferredConfiguration());
        ventana.add(canvas, BorderLayout.CENTER);
        ventana.setVisible(true);

        universo = new SimpleUniverse(canvas);
        BranchGroup escena = crearEscena();

        camaraTG = universo.getViewingPlatform().getViewPlatformTransform();
        actualizarPosicionCamara();

        escena.compile();
        universo.addBranchGraph(escena);

        reproducirMusica("src/sound/horror.wav");

        keyAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                Vector3f nuevaPosicion = new Vector3f(posicionCamara);
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W:
                        nuevaPosicion.z -= 0.1f;
                        break;
                    case KeyEvent.VK_S:
                        nuevaPosicion.z += 0.1f;
                        break;
                    case KeyEvent.VK_A:
                        nuevaPosicion.x -= 0.1f;
                        break;
                    case KeyEvent.VK_D:
                        nuevaPosicion.x += 0.1f;
                        break;
                }
                if (!hayColision(nuevaPosicion) && dentroDeLimites(nuevaPosicion)) {
                    posicionCamara.set(nuevaPosicion);
                    actualizarPosicionCamara();
                }
            }
        };
        canvas.addKeyListener(keyAdapter);

        mouseMotionListener = new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int deltaX = e.getX() - lastMousePosition.x;
                int deltaY = e.getY() - lastMousePosition.y;

                anguloY += deltaX * SENSITIVITY;
                anguloX -= deltaY * SENSITIVITY;

                anguloX = Math.max(-((float) Math.PI / 2), Math.min((float) Math.PI / 2, anguloX));
                anguloY = anguloY % ((float) (2 * Math.PI));

                actualizarRotacionCamara();

                lastMousePosition = e.getPoint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                lastMousePosition = e.getPoint();
            }
        };
        canvas.addMouseMotionListener(mouseMotionListener);

        canvas.setFocusable(true);
        canvas.requestFocus();
    }

    private static void actualizarPosicionCamara() {
        camaraTransformacion.setTranslation(posicionCamara);
        camaraTG.setTransform(camaraTransformacion);
    }

    private static void actualizarRotacionCamara() {
        Transform3D rotacionX = new Transform3D();
        rotacionX.rotX(anguloX);

        Transform3D rotacionY = new Transform3D();
        rotacionY.rotY(anguloY);

        camaraTransformacion.setIdentity();
        camaraTransformacion.mul(rotacionY);
        camaraTransformacion.mul(rotacionX);
        camaraTransformacion.setTranslation(posicionCamara);

        camaraTG.setTransform(camaraTransformacion);
    }

    private static boolean hayColision(Vector3f nuevaPosicion) {
        for (Box edificio : edificios) {
            Transform3D transformacion = new Transform3D();
            edificio.getLocalToVworld(transformacion);
            Vector3f posicionEdificio = new Vector3f();
            transformacion.get(posicionEdificio);

            float distancia = (float) Math.sqrt(
                    Math.pow(nuevaPosicion.x - posicionEdificio.x, 2) +
                            Math.pow(nuevaPosicion.z - posicionEdificio.z, 2)
            );

            if (distancia < COLLISION_RADIUS) {
                return true;
            }
        }

        for (TransformGroup enemigoTG : enemigos) {
            Transform3D transformacion = new Transform3D();
            enemigoTG.getTransform(transformacion);
            Vector3f posicionEnemigo = new Vector3f();
            transformacion.get(posicionEnemigo);

            float distancia = (float) Math.sqrt(
                    Math.pow(nuevaPosicion.x - posicionEnemigo.x, 2) +
                            Math.pow(nuevaPosicion.z - posicionEnemigo.z, 2)
            );

            if (distancia < COLLISION_RADIUS) {
                mostrarGameOver();
                return true;
            }
        }

        return false;
    }

    private static boolean dentroDeLimites(Vector3f nuevaPosicion) {
        return Math.abs(nuevaPosicion.x) <= LIMITE_ESCENARIO && Math.abs(nuevaPosicion.z) <= LIMITE_ESCENARIO;
    }

    private static BranchGroup crearEscena() {
        BranchGroup grupoPrincipal = new BranchGroup();

        Background fondo = new Background(new Color3f(0.1f, 0.01f, 0.01f));
        fondo.setApplicationBounds(new BoundingSphere(new Point3d(0.0, 0.0, 0.0), 100.0));
        grupoPrincipal.addChild(fondo);

        grupoPrincipal.addChild(crearLuna());

        for (int i = 0; i < 20; i++) {
            String texturePath = "src/img/edificio" + ((i % 5) + 1) + ".jpg";
            float x = (float) (Math.random() * 10.0f - 5.0f);
            float z = (float) (Math.random() * 10.0f - 5.0f);
            TransformGroup edificio = crearEdificio(x, -0.5f, z, 0.3f, 1.0f + Math.random(), texturePath);
            grupoPrincipal.addChild(edificio);
        }

        for (int i = 0; i < 10; i++) {
            float x = (float) (Math.random() * 10.0f - 5.0f);
            float z = (float) (Math.random() * 10.0f - 5.0f);
            TransformGroup enemigo = crearEnemigo(x, -0.7f, z, 0.1f, 0.5f, "src/img/enemigo.jpg");
            enemigos.add(enemigo);
            grupoPrincipal.addChild(enemigo);
        }

        grupoPrincipal.addChild(crearSuelo());

        grupoPrincipal.addChild(crearLuces());

        iniciarAnimacionEnemigos();
        return grupoPrincipal;
    }

    private static TransformGroup crearLuna() {
        Transform3D transformacionLuna = new Transform3D();
        transformacionLuna.setTranslation(new Vector3f(2.0f, 2.5f, -6.0f));

        TransformGroup grupoLuna = new TransformGroup(transformacionLuna);

        Appearance aparienciaLuna = new Appearance();

        TextureLoader loader = new TextureLoader("src/img/luna.jpg", null);
        Texture textura = loader.getTexture();
        aparienciaLuna.setTexture(textura);

        Material materialLuna = new Material(
                new Color3f(1.0f, 1.0f, 1.0f),
                new Color3f(0.0f, 0.0f, 0.0f),
                new Color3f(1.0f, 1.0f, 1.0f),
                new Color3f(1.0f, 1.0f, 1.0f),
                64.0f
        );
        aparienciaLuna.setMaterial(materialLuna);

        Sphere luna = new Sphere(0.7f, Sphere.GENERATE_TEXTURE_COORDS | Sphere.GENERATE_NORMALS, 50, aparienciaLuna);
        grupoLuna.addChild(luna);

        PointLight luzLuna = new PointLight(
                new Color3f(1.0f, 1.0f, 1.0f), new Point3f(2.0f, 2.5f, -6.0f), new Point3f(1.0f, 0.0f, 0.0f)
        );
        luzLuna.setInfluencingBounds(new BoundingSphere(new Point3d(0.0, 0.0, 0.0), 100.0));
        grupoLuna.addChild(luzLuna);

        return grupoLuna;
    }

    private static TransformGroup crearEdificio(float x, float y, float z, float ancho, double altura, String texturePath) {
        Transform3D transformacion = new Transform3D();
        transformacion.setTranslation(new Vector3f(x, y, z));

        TransformGroup grupoTransformado = new TransformGroup(transformacion);

        Appearance aparienciaEdificio = new Appearance();

        TextureLoader loader = new TextureLoader(texturePath, null);
        Texture textura = loader.getTexture();
        aparienciaEdificio.setTexture(textura);

        Material materialEdificio = new Material(
                new Color3f(1.0f, 1.0f, 1.0f),
                new Color3f(0.0f, 0.0f, 0.0f),
                new Color3f(1.0f, 1.0f, 1.0f),
                new Color3f(1.0f, 1.0f, 1.0f),
                64.0f // Brillo
        );
        aparienciaEdificio.setMaterial(materialEdificio);

        Box edificio = new Box(ancho, (float) (altura * 0.5), ancho, Box.GENERATE_TEXTURE_COORDS | Box.GENERATE_NORMALS, aparienciaEdificio);
        grupoTransformado.addChild(edificio);
        edificios.add(edificio);

        // Habilitar la generación de sombras
        edificio.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
        aparienciaEdificio.setCapability(Appearance.ALLOW_MATERIAL_WRITE);
        aparienciaEdificio.setCapability(Appearance.ALLOW_TEXTURE_WRITE);

        return grupoTransformado;
    }

    private static TransformGroup crearSuelo() {
        Transform3D transformacionSuelo = new Transform3D();
        transformacionSuelo.setTranslation(new Vector3f(0.0f, -1.0f, 0.0f));

        TransformGroup grupoSuelo = new TransformGroup(transformacionSuelo);

        Appearance aparienciaSuelo = new Appearance();

        TextureLoader loader = new TextureLoader("src/img/suelo.jpg", null);
        Texture textura = loader.getTexture();
        textura.setBoundaryModeS(Texture.WRAP);
        textura.setBoundaryModeT(Texture.WRAP);

        TextureAttributes texAttr = new TextureAttributes();
        texAttr.setTextureMode(TextureAttributes.REPLACE);
        aparienciaSuelo.setTextureAttributes(texAttr);
        aparienciaSuelo.setTexture(textura);

        Box suelo = new Box(5.0f, 0.1f, 5.0f, Box.GENERATE_TEXTURE_COORDS | Box.GENERATE_NORMALS, aparienciaSuelo);
        grupoSuelo.addChild(suelo);

        return grupoSuelo;
    }

    private static TransformGroup crearEnemigo(float x, float y, float z, float radio, float altura, String texturePath) {
        x = Math.max(-LIMITE_ESCENARIO, Math.min(LIMITE_ESCENARIO, x));
        z = Math.max(-LIMITE_ESCENARIO, Math.min(LIMITE_ESCENARIO, z));

        Transform3D transformacion = new Transform3D();
        transformacion.setTranslation(new Vector3f(x, y, z));

        TransformGroup grupoTransformado = new TransformGroup(transformacion);
        grupoTransformado.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);

        Appearance aparienciaEnemigo = new Appearance();

        TextureLoader loader = new TextureLoader(texturePath, null);
        Texture textura = loader.getTexture();
        aparienciaEnemigo.setTexture(textura);

        Material materialEnemigo = new Material(
                new Color3f(1.0f, 1.0f, 1.0f),
                new Color3f(0.0f, 0.0f, 0.0f),
                new Color3f(1.0f, 1.0f, 1.0f),
                new Color3f(1.0f, 1.0f, 1.0f),
                64.0f
        );
        aparienciaEnemigo.setMaterial(materialEnemigo);

        Cylinder enemigo = new Cylinder(radio, altura, Cylinder.GENERATE_TEXTURE_COORDS | Cylinder.GENERATE_NORMALS, aparienciaEnemigo);
        grupoTransformado.addChild(enemigo);

        return grupoTransformado;
    }

    private static void iniciarAnimacionEnemigos() {
        timer.scheduleAtFixedRate(new TimerTask() {
            private boolean moverEnX = true;
            private boolean direccionPositiva = true;

            @Override
            public void run() {
                for (TransformGroup enemigoTG : enemigos) {
                    Transform3D transformacion = new Transform3D();
                    enemigoTG.getTransform(transformacion);

                    Vector3f posicion = new Vector3f();
                    transformacion.get(posicion);

                    if (moverEnX) {
                        if (direccionPositiva) {
                            posicion.x += 0.05f;
                            if (posicion.x > LIMITE_ESCENARIO) {
                                direccionPositiva = false;
                            }
                        } else {
                            posicion.x -= 0.05f;
                            if (posicion.x < -LIMITE_ESCENARIO) {
                                direccionPositiva = true;
                            }
                        }
                    } else {
                        if (direccionPositiva) {
                            posicion.z += 0.05f;
                            if (posicion.z > LIMITE_ESCENARIO) {
                                direccionPositiva = false;
                            }
                        } else {
                            posicion.z -= 0.05f;
                            if (posicion.z < -LIMITE_ESCENARIO) {
                                direccionPositiva = true;
                            }
                        }
                    }

                    moverEnX = !moverEnX;

                    if (Math.abs(posicion.x) > LIMITE_ESCENARIO || Math.abs(posicion.z) > LIMITE_ESCENARIO) {
                        posicion.x = (float) (Math.random() * 2 * LIMITE_ESCENARIO - LIMITE_ESCENARIO);
                        posicion.z = (float) (Math.random() * 2 * LIMITE_ESCENARIO - LIMITE_ESCENARIO);
                    }

                    Transform3D rotacion = new Transform3D();
                    rotacion.rotY(0.05); // Ángulo de rotación
                    transformacion.mul(rotacion);

                    transformacion.setTranslation(posicion);
                    enemigoTG.setTransform(transformacion);
                }
            }
        }, 0, 50);
    }

    private static BranchGroup crearLuces() {
        BranchGroup luces = new BranchGroup();

        AmbientLight luzAmbiental = new AmbientLight(new Color3f(0.3f, 0.3f, 0.3f));
        luzAmbiental.setInfluencingBounds(new BoundingSphere(new Point3d(0.0, 0.0, 0.0), 100.0));
        luces.addChild(luzAmbiental);

        DirectionalLight luzDireccional = new DirectionalLight(
                new Color3f(1.0f, 1.0f, 1.0f), new Vector3f(-1.0f, -1.0f, -1.0f)
        );
        luzDireccional.setInfluencingBounds(new BoundingSphere(new Point3d(0.0, 0.0, 0.0), 100.0));
        luces.addChild(luzDireccional);

        return luces;
    }

    private static void mostrarGameOver() {
        posicionCamara.set(2.0f, 2.5f, -3.5f);
        anguloX = 0.0f;
        anguloY = 0.0f;
        actualizarPosicionCamara();
        actualizarRotacionCamara();

        Font3D font3D = new Font3D(new Font("Helvetica", Font.PLAIN, 1), new FontExtrusion());
        Text3D texto3D = new Text3D(font3D, "Game Over", new Point3f(0.0f, 0.0f, 0.0f));
        Appearance aparienciaTexto = new Appearance();
        aparienciaTexto.setMaterial(new Material(new Color3f(1.0f, 0.0f, 0.0f), new Color3f(0.0f, 0.0f, 0.0f),
                new Color3f(1.0f, 0.0f, 0.0f), new Color3f(1.0f, 1.0f, 1.0f), 64.0f));

        Shape3D shape3D = new Shape3D(texto3D, aparienciaTexto);
        TransformGroup tg = new TransformGroup();
        tg.addChild(shape3D);

        Transform3D transformacionTexto = new Transform3D();
        transformacionTexto.setScale(0.2);
        transformacionTexto.setTranslation(new Vector3f(1.5f, 2.5f, -5.0f));
        tg.setTransform(transformacionTexto);

        BranchGroup textoBG = new BranchGroup();
        textoBG.addChild(tg);

        universo.addBranchGraph(textoBG);

        detenerJuego();
    }

    private static void detenerJuego() {
        timer.cancel();
        canvas.removeKeyListener(keyAdapter);
        canvas.removeMouseMotionListener(mouseMotionListener);
    }

    private static void reproducirMusica(String rutaArchivo) {
        try {
            File archivo = new File(rutaArchivo);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(archivo);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);

            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.out.println("Error al reproducir música: " + e.getMessage());
        }
    }
}