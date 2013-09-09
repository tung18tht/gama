package msi.gama.jogl.utils;

import static javax.media.opengl.GL.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.*;
import javax.media.opengl.*;
import javax.media.opengl.glu.GLU;
import msi.gama.jogl.JOGLAWTDisplaySurface;
import msi.gama.jogl.scene.*;
import msi.gama.jogl.utils.Camera.*;
import msi.gama.jogl.utils.Camera.Arcball.Vector3D;
import msi.gama.jogl.utils.JTSGeometryOpenGLDrawer.ShapeFileReader;
import msi.gama.metamodel.shape.*;
import msi.gama.outputs.OutputSynchronizer;
import utils.GLUtil;
import com.sun.opengl.util.*;
import com.sun.opengl.util.texture.*;

public class JOGLAWTGLRenderer implements GLEventListener {

	public GLU glu;
	public GL gl;
	public GLUT glut;

	// ///Static members//////
	// private static final boolean USE_VERTEX_ARRAY = false;
	private static final int REFRESH_FPS = 30;

	private static boolean BLENDING_ENABLED; // blending on/off
	private static boolean IS_LIGHT_ON;

	public final FPSAnimator animator;
	private GLContext context;
	public GLCanvas canvas;

	private int width, height;

	// Camera
	public ICamera camera;

	// Lighting
	private Color ambientLightValue;
	private Color diffuseLightValue;
	// Blending

	public JOGLAWTDisplaySurface displaySurface;
	private ModelScene scene;

	// Use multiple view port
	public final boolean multipleViewPort = false;
	// Display model a a 3D Cube
	private final boolean CubeDisplay = false;
	// Handle Shape file
	public ShapeFileReader myShapeFileReader;
	// use glut tesselation or JTS tesselation
	// facet "tesselation"
	private boolean useTessellation = true;
	// facet "inertia"
	private boolean inertia = false;
	// facet "inertia"
	private boolean stencil = false;
	// facet "drawEnv"
	private boolean drawEnv = false;
	// facet "show_fps"
	private boolean showFPS = false;
	// facet "z_fighting"
	private boolean z_fighting = false;

	public boolean triangulation = false;

	public boolean drawAxes = true;
	// Display or not the triangle when using triangulation (useTessellation = false)
	private boolean polygonMode = true;
	// Show JTS (GAMA) triangulation
	public boolean JTSTriangulation = false;
	// is in picking mode ?
	private boolean picking = false;

	public int pickedObjectIndex = -1;
	public ISceneObject currentPickedObject;

	public int frame = 0;

	int[] viewport = new int[4];
	double mvmatrix[] = new double[16];
	double projmatrix[] = new double[16];

	private double startTime = 0;
	private int frameCount = 0;
	private double currentTime = 0;
	private double previousTime = 0;
	public float fps = 00.00f;

	public boolean autoSwapBuffers = false;
	public boolean disableManualBufferSwapping;
	public boolean colorPicking = false;

	public JOGLAWTGLRenderer(final JOGLAWTDisplaySurface d) {
		// Enabling the stencil buffer
		final GLCapabilities cap = new GLCapabilities();
		cap.setStencilBits(8);
		// Initialize the user camera
		displaySurface = d;
		camera = new CameraArcBall(this);
		canvas = new GLCanvas(cap);
		// use for color picking
		canvas.setAutoSwapBufferMode(autoSwapBuffers);
		canvas.addGLEventListener(this);
		canvas.addKeyListener(camera);
		canvas.addMouseListener(camera);
		canvas.addMouseMotionListener(camera);
		canvas.addMouseWheelListener(camera);
		canvas.setVisible(true);
		canvas.setFocusable(true); // To receive key event
		canvas.requestFocusInWindow();
		animator = new FPSAnimator(canvas, REFRESH_FPS, true);

	}

	@Override
	public void init(final GLAutoDrawable drawable) {
		startTime = System.currentTimeMillis();
		width = drawable.getWidth();
		height = drawable.getHeight();
		gl = drawable.getGL();
		glu = new GLU();
		glut = new GLUT();
		setContext(drawable.getContext());

		// Set background color

		setBackground(displaySurface.getBackgroundColor());
		// Enable smooth shading, which blends colors nicely, and smoothes out lighting.
		GLUtil.enableSmooth(gl);

		// Perspective correction
		gl.glHint(GL_PERSPECTIVE_CORRECTION_HINT, GL_NICEST);
		GLUtil.enableDepthTest(gl);

		// Set up the lighting for Light-1
		GLUtil.InitializeLighting(gl, glu, (float) displaySurface.getEnvWidth(), (float) displaySurface.getEnvHeight(),
			ambientLightValue, diffuseLightValue);

		// PolygonMode (Solid or lines)
		if ( polygonMode ) {
			gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_FILL);
		} else {
			gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_LINE);
		}
		// Blending control
		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
		gl.glEnable(GL_BLEND);
		// gl.glDisable(GL_DEPTH_TEST);
		// FIXME : should be turn on only if need (if we draw image)
		// problem when true with glutBitmapString
		BLENDING_ENABLED = true;
		IS_LIGHT_ON = true;

		camera.updateCamera(gl, glu, width, height);
		scene = new ModelScene(this);

		OutputSynchronizer.decInitializingViews(this.displaySurface.getOutputName());

	}

	private Color background = Color.white;

	public void setBackground(final Color c) {
		background = c;
		canvas.setBackground(c);
	}

	@Override
	public void display(final GLAutoDrawable drawable) {

		if ( !displaySurface.isPaused() ) {
			gl = drawable.getGL();
			setContext(drawable.getContext());
			gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
			gl.glGetDoublev(GL.GL_MODELVIEW_MATRIX, mvmatrix, 0);
			gl.glGetDoublev(GL.GL_PROJECTION_MATRIX, projmatrix, 0);

			// Clear the screen and the depth buffer
			gl.glClearDepth(1.0f);
			gl.glClearColor(background.getRed() / 255.0f, background.getGreen() / 255.0f,
				background.getBlue() / 255.0f, 1.0f);
			gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

			gl.glMatrixMode(GL.GL_PROJECTION);
			// Reset the view (x, y, z axes back to normal)
			gl.glLoadIdentity();

			camera.updateCamera(gl, glu, width, height);

			if ( IS_LIGHT_ON ) {
				gl.glEnable(GL_LIGHTING);
			} else {
				gl.glDisable(GL_LIGHTING);
			}

			// Draw Diffuse light as yellow sphere
			// GLUtil.DrawDiffuseLights(gl, glu,getMaxEnvDim()/10);

			// FIXME: Now the background is not updated but it should to have a night effect.
			// Set background color
			// gl.glClearColor(ambiantLightValue.floatValue(), ambiantLightValue.floatValue(),
			// ambiantLightValue.floatValue(), 1.0f);
			// The ambiant_light is always reset in case of dynamic lighting.
			GLUtil.UpdateAmbiantLight(gl, glu, ambientLightValue);
			GLUtil.UpdateDiffuseLight(gl, glu, diffuseLightValue);

			// Show triangulated polygon or not (trigger by GAMA)
			/*
			 * if ( !triangulation ) {
			 * gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_FILL);
			 * } else {
			 * gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_LINE);
			 * }
			 */

			// Blending control

			if ( BLENDING_ENABLED ) {
				gl.glEnable(GL_BLEND); // Turn blending on

			} else {
				gl.glDisable(GL_BLEND); // Turn blending off
				if ( !getStencil() ) {
					gl.glEnable(GL_DEPTH_TEST);
				} else {
					gl.glEnable(GL_STENCIL_TEST);
				}
			}

			// Use polygon offset for a better edges rendering
			// (http://www.glprogramming.com/red/chapter06.html#name4)
			// gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
			// gl.glPolygonOffset(1, 1);

			// gl.glDisable(GL_DEPTH_TEST);

			// Show triangulated polygon or not (trigger by GAMA)
			if ( !triangulation ) {
				gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_FILL);
			} else {
				gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_LINE);
			}

			this.rotateModel();

			if ( getInertia() ) {
				camera.doInertia();
			}

			this.drawScene();

			// this.DrawShapeFile();
			gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);
			gl.glPopMatrix();

			// ROI drawer
			if ( this.displaySurface.selectRectangle ) {
				drawROI();
			}

			// Show fps for performance mesures
			if ( this.getShowFPS() ) {
				CalculateFrameRate();
				gl.glDisable(GL_BLEND);
				gl.glColor4d(0.0, 0.0, 0.0, 1.0d);
				gl.glRasterPos3d(-this.getWidth()/10, this.getHeight()/10, 0);
				gl.glScaled(8.0d, 8.0d, 8.0d);
				glut.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_10, "fps : " + fps);
				gl.glScaled(0.125d, 0.125d, 0.125d);
				gl.glEnable(GL_BLEND);
			}

			if ( !autoSwapBuffers ) {
				if ( disableManualBufferSwapping ) {
					disableManualBufferSwapping = false;
				} else {
					canvas.swapBuffers();
				}
			}

		}
	}

	@Override
	public void reshape(final GLAutoDrawable drawable, final int arg1, final int arg2, final int width, final int height) {
		// Get the OpenGL graphics context
		gl = drawable.getGL();
		this.width = width;
		this.height = height == 0 ? 1 : height;

		// final float aspect = (float) width / height;
		// Set the viewport (display area) to cover the entire window
		gl.glViewport(0, 0, width, height);
		// Enable the model view - any new transformations will affect the model-view matrix
		gl.glMatrixMode(GL_MODELVIEW);
		gl.glLoadIdentity(); // reset
		// perspective view
		gl.glMatrixMode(GL.GL_PROJECTION);
		gl.glLoadIdentity();
		// glu.gluPerspective(45.0f, aspect, 0.1f, getMaxEnvDim() * 100);
		// FIXME Update camera as well ??
		camera.updateCamera(gl, glu, width, height);
	}

	@Override
	public void displayChanged(final GLAutoDrawable arg0, final boolean arg1, final boolean arg2) {}

	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * Once the list of JTSGeometries has been created, OpenGL display call this
	 * method every framerate. FIXME: Need to be optimize with the use of Vertex
	 * Array or even VBO
	 * @param picking
	 * 
	 */
	public void drawModel() {
		scene.draw(this, isPicking() || currentPickedObject != null, drawEnv);
	}

	public void drawScene() {
		// gl.glViewport(0, 0, width, height);
		if ( isPicking() ) {
			this.drawPickableObjects();
		} else {
			if ( CubeDisplay ) {
				drawCubeDisplay((float) displaySurface.getEnvWidth());

			} else {
				this.drawModel();
			}
		}
	}

	private void drawCubeDisplay(final float width) {
		final float envMaxDim = width;
		this.drawModel();
		gl.glTranslatef(envMaxDim, 0, 0);
		gl.glRotatef(90, 0, 1, 0);
		this.drawModel();
		gl.glTranslatef(envMaxDim, 0, 0);
		gl.glRotatef(90, 0, 1, 0);
		this.drawModel();
		gl.glTranslatef(envMaxDim, 0, 0);
		gl.glRotatef(90, 0, 1, 0);
		this.drawModel();
		gl.glTranslatef(envMaxDim, 0, 0);
		gl.glRotatef(90, 0, 1, 0);
		gl.glRotatef(-90, 1, 0, 0);
		gl.glTranslatef(0, envMaxDim, 0);
		this.drawModel();
		gl.glTranslatef(0, -envMaxDim, 0);
		gl.glRotatef(90, 1, 0, 0);
		gl.glRotatef(90, 1, 0, 0);
		gl.glTranslatef(0, 0, envMaxDim);
		this.drawModel();
		gl.glTranslatef(0, 0, -envMaxDim);
		gl.glRotatef(-90, 1, 0, 0);
	}

	public void switchCamera() {
		canvas.removeKeyListener(camera);
		canvas.removeMouseListener(camera);
		canvas.removeMouseMotionListener(camera);
		canvas.removeMouseWheelListener(camera);

		if ( displaySurface.switchCamera ) {
			camera = new FreeFlyCamera(this);
		} else {
			camera = new CameraArcBall(this);
		}

		canvas.addKeyListener(camera);
		canvas.addMouseListener(camera);
		canvas.addMouseMotionListener(camera);
		canvas.addMouseWheelListener(camera);

	}

	int minAntiAliasing = GL_NEAREST; /* GL_NEAREST_MIPMAP_NEAREST; */
	int magAntiAliasing = GL_NEAREST;

	public void setAntiAliasing(final boolean antialias) {
		// antialiasing = antialias ? GL_LINEAR : GL_NEAREST;
		minAntiAliasing = antialias ? GL_LINEAR : GL_NEAREST; /* GL_LINEAR_MIPMAP_LINEAR : GL_NEAREST_MIPMAP_NEAREST; */
		magAntiAliasing = antialias ? GL_LINEAR : GL_NEAREST;
	}

	public MyTexture createTexture(final BufferedImage image, final boolean isDynamic, final int index) {
		// Create a OpenGL Texture object from (URL, mipmap, file suffix)
		// need to have an opengl context valide
		this.getContext().makeCurrent();
		Texture texture;
		try {
			texture = TextureIO.newTexture(image, false /* true for mipmapping */);
		} catch (final GLException e) {
			return null;
		}
		// FIXME:need to see the effect of this bending
		gl.glBindTexture(GL.GL_TEXTURE_2D, index);
		texture.setTexParameteri(GL_TEXTURE_MIN_FILTER, minAntiAliasing);
		texture.setTexParameteri(GL_TEXTURE_MAG_FILTER, magAntiAliasing);
		final MyTexture curTexture = new MyTexture();
		curTexture.texture = texture;
		curTexture.isDynamic = isDynamic;
		// GuiUtils.debug("JOGLAWTGLRenderer.createTexture for " + image);
		this.getContext().release();
		return curTexture;
	}

	public void drawPickableObjects() {
		if ( camera.beginPicking(gl) ) {
			drawModel();
			setPickedObjectIndex(camera.endPicking(gl));
		}
		drawModel();
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public GLContext getContext() {
		return context;
	}

	public void setContext(final GLContext context) {
		this.context = context;
	}

	public void setAmbientLightValue(final Color ambientLightValue) {
		this.ambientLightValue = ambientLightValue;
	}

	public void setDiffuseLightValue(final Color diffuseLightValue) {
		this.diffuseLightValue = diffuseLightValue;
	}

	public void setPolygonMode(final boolean polygonMode) {
		this.polygonMode = polygonMode;
	}

	public boolean getTessellation() {
		return useTessellation;
	}

	public void setTessellation(final boolean tess) {
		this.useTessellation = tess;
	}

	public void setInertia(final boolean iner) {
		this.inertia = iner;
	}

	public boolean getInertia() {
		return inertia;
	}

	public void setStencil(final boolean st) {
		this.stencil = st;
	}

	public boolean getStencil() {
		return stencil;
	}

	public void setZFighting(final boolean z) {
		this.z_fighting = z;
	}

	public boolean getZFighting() {
		return z_fighting;
	}

	public void setShowFPS(final boolean fps) {
		this.showFPS = fps;
	}

	public boolean getShowFPS() {
		return showFPS;
	}

	public void setDrawEnv(final boolean denv) {
		this.drawEnv = denv;
	}

	public boolean getDrawEnv() {
		return drawEnv;
	}

	public void setCameraPosition(final ILocation cameraPos) {
		if ( cameraPos.equals(new GamaPoint(-1, -1, -1)) ) {// No change;
		} else {
			camera.updatePosition(cameraPos.getX(), cameraPos.getY(), cameraPos.getZ());
		}
	}

	public void setCameraLookPosition(final ILocation camLookPos) {
		if ( camLookPos.equals(new GamaPoint(-1, -1, -1)) ) {// No change
		} else {
			camera.lookPosition(camLookPos.getX(), camLookPos.getY(), camLookPos.getZ());
		}
	}

	public void setCameraUpVector(final ILocation upVector) {
		if ( camera.getPhi() < 360 && camera.getPhi() > 180 ) {
			camera.upPosition(0, -1, 0);
		} else {
			camera.upPosition(upVector.getX(), upVector.getY(), upVector.getZ());
		}
	}

	public double getMaxEnvDim() {
		double env_width = displaySurface.getEnvWidth();
		double env_height = displaySurface.getEnvHeight();
		return env_width > env_height ? env_width : env_height;
	}

	public void setPickedObjectIndex(final int pickedObjectIndex) {
		this.pickedObjectIndex = pickedObjectIndex;
		if ( pickedObjectIndex == -1 ) {
			setPicking(false);
		} else if ( pickedObjectIndex == -2 ) {
			displaySurface.selectAgents(null, 0);
			setPicking(false);
		}
	}

	//
	// public void cleanListsAndVertices() {
	// if ( USE_VERTEX_ARRAY ) {
	// graphicsGLUtils.vertexArrayHandler.DeleteVertexArray();
	// }
	// }

	public ModelScene getScene() {
		return scene;
	}

	public void dispose() {
		scene.dispose();
	}

	public void CalculateFrameRate() {

		// Increase frame count
		frameCount++;

		// Get the number of milliseconds since display started
		currentTime = System.currentTimeMillis() - startTime;

		// Calculate time passed
		int timeInterval = (int) (currentTime - previousTime);
		if ( timeInterval > 1000 ) {
			// calculate the number of frames per second
			fps = frameCount / (timeInterval / 1000.0f);

			// Set time
			previousTime = currentTime;

			// Reset frame count
			frameCount = 0;
		}

	}

	// Use when the rotation button is on.
	public void rotateModel() {
		if ( this.displaySurface.rotation ) {
			frame++;
		}
		if ( frame != 0 ) {
			double env_width = displaySurface.getEnvWidth();
			double env_height = displaySurface.getEnvHeight();
			gl.glTranslated(env_width / 2, -env_height / 2, 0);
			gl.glRotatef(frame, 0, 0, 1);
			gl.glTranslated(-env_width / 2, +env_height / 2, 0);
		}
	}

	// ////////////////////////ROI HANDLER ////////////////////////////////////
	public Point2D.Double getRealWorldPointFromWindowPoint(final Point windowPoint) {
		if ( glu == null ) { return null; }
		int realy = 0;// GL y coord pos
		double[] wcoord = new double[4];// wx, wy, wz;// returned xyz coords

		int x = (int) windowPoint.getX(), y = (int) windowPoint.getY();

		realy = viewport[3] - y;

		glu.gluUnProject(x, realy, 0.1, mvmatrix, 0, projmatrix, 0, viewport, 0, wcoord, 0);
		Vector3D v1 = new Vector3D(wcoord[0], wcoord[1], wcoord[2]);

		glu.gluUnProject(x, realy, 0.9, mvmatrix, 0, projmatrix, 0, viewport, 0, wcoord, 0);
		Vector3D v2 = new Vector3D(wcoord[0], wcoord[1], wcoord[2]);

		Vector3D v3 = v2.subtract(v1);
		v3.normalize();
		float distance = (float) (camera.getPosition().getZ() / Vector3D.dotProduct(new Vector3D(0.0, 0.0, -1.0), v3));
		Vector3D worldCoordinates = camera.getPosition().add(v3.scalarMultiply(distance));

		final Point2D.Double realWorldPoint = new Point2D.Double(worldCoordinates.x, worldCoordinates.y);
		return realWorldPoint;
	}

	public Point getIntWorldPointFromWindowPoint(final Point windowPoint) {
		Point2D.Double p = getRealWorldPointFromWindowPoint(windowPoint);
		return new Point((int) p.x, (int) p.y);
	}

	public Point2D.Double getWindowPointPointFromRealWorld(final Point realWorldPoint) {
		if ( glu == null ) { return null; }

		DoubleBuffer model = DoubleBuffer.allocate(16);
		gl.glGetDoublev(GL.GL_MODELVIEW_MATRIX, model);

		DoubleBuffer proj = DoubleBuffer.allocate(16);
		gl.glGetDoublev(GL.GL_PROJECTION_MATRIX, proj);

		IntBuffer view = IntBuffer.allocate(4);
		gl.glGetIntegerv(GL.GL_VIEWPORT, view);

		DoubleBuffer winPos = DoubleBuffer.allocate(3);
		glu.gluProject(realWorldPoint.x, realWorldPoint.y, 0, model, proj, view, winPos);

		final Point2D.Double WindowPoint = new Point2D.Double(winPos.get(), viewport[3] - winPos.get());
		return WindowPoint;
	}

	public double GetEnvWidthOnScreen() {
		Point realWorld = new Point(0, 0);
		Point2D.Double WindowPoint = getWindowPointPointFromRealWorld(realWorld);

		Point realWorld2 = new Point((int) displaySurface.getEnvWidth(), -(int) displaySurface.getEnvHeight());
		Point2D.Double WindowPoint2 = getWindowPointPointFromRealWorld(realWorld2);
		if ( WindowPoint2 == null || WindowPoint == null ) { return 0.0; }
		return WindowPoint2.x - WindowPoint.x;
	}

	public double GetEnvHeightOnScreen() {
		Point realWorld = new Point(0, 0);
		Point2D.Double WindowPoint = getWindowPointPointFromRealWorld(realWorld);

		Point realWorld2 = new Point((int) displaySurface.getEnvWidth(), -(int) displaySurface.getEnvHeight());
		Point2D.Double WindowPoint2 = getWindowPointPointFromRealWorld(realWorld2);

		return WindowPoint2.y - WindowPoint.y;
	}

	public void drawROI() {
		if ( camera.isEnableROIDrawing() ) {
			Point realPressedPoint = getIntWorldPointFromWindowPoint(camera.getLastMousePressedPosition());
			Point realMousePositionPoint = getIntWorldPointFromWindowPoint(camera.getMousePosition());
			drawROI(gl, realPressedPoint.x, -realPressedPoint.y, realMousePositionPoint.x, -realMousePositionPoint.y,
				this.getZFighting(), this.getMaxEnvDim());
			camera.setRegionOfInterest(realPressedPoint, realMousePositionPoint);
		}

	}

	public void setPicking(final boolean value) {
		picking = value;
		// GuiUtils.debug("JOGLAWTDisplaySurface.setPicking " + value);
		if ( !value ) {
			if ( currentPickedObject != null ) {
				currentPickedObject.unpick();
				currentPickedObject = null;
			}
			pickedObjectIndex = -1;
		}
	}

	public boolean isPicking() {
		return picking;
	}

	private void drawROI(final GL gl, final double x1, final double y1, final double x2, final double y2,
		final boolean z_fighting, final double maxEnvDim) {

		if ( z_fighting ) {
			gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_LINE);
			gl.glEnable(GL.GL_POLYGON_OFFSET_LINE);
			// Draw on top of everything
			gl.glPolygonOffset(0.0f, (float) -maxEnvDim);
			gl.glBegin(GL.GL_POLYGON);

			gl.glVertex3d(x1, -y1, 0.0f);
			gl.glVertex3d(x2, -y1, 0.0f);

			gl.glVertex3d(x2, -y1, 0.0f);
			gl.glVertex3d(x2, -y2, 0.0f);

			gl.glVertex3d(x2, -y2, 0.0f);
			gl.glVertex3d(x1, -y2, 0.0f);

			gl.glVertex3d(x1, -y2, 0.0f);
			gl.glVertex3d(x1, -y1, 0.0f);
			gl.glEnd();
			gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL.GL_FILL);
		} else {
			gl.glBegin(GL.GL_LINES);

			gl.glVertex3d(x1, -y1, 0.0f);
			gl.glVertex3d(x2, -y1, 0.0f);

			gl.glVertex3d(x2, -y1, 0.0f);
			gl.glVertex3d(x2, -y2, 0.0f);

			gl.glVertex3d(x2, -y2, 0.0f);
			gl.glVertex3d(x1, -y2, 0.0f);

			gl.glVertex3d(x1, -y2, 0.0f);
			gl.glVertex3d(x1, -y1, 0.0f);
			gl.glEnd();
		}

	}
}
