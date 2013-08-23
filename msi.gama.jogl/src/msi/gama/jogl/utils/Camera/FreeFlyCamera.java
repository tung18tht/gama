package msi.gama.jogl.utils.Camera;

import static java.awt.event.KeyEvent.*;
import java.awt.Point;
import java.awt.event.*;
import java.util.Iterator;
import javax.media.opengl.GL;
import javax.media.opengl.glu.GLU;
import msi.gama.jogl.utils.JOGLAWTGLRenderer;
import msi.gama.jogl.utils.Camera.Arcball.Vector3D;
import msi.gama.metamodel.agent.IAgent;
import msi.gama.metamodel.shape.*;
import msi.gama.metamodel.topology.AbstractTopology;
import msi.gama.metamodel.topology.filter.Different;
import msi.gama.runtime.GAMA;
import com.vividsolutions.jts.geom.Envelope;

public class FreeFlyCamera extends AbstractCamera {

	public Vector3D _forward;
	public Vector3D _left;

	public double _speed;
	public double _sensivity;

	public FreeFlyCamera(final JOGLAWTGLRenderer renderer) {
		super(renderer);
		_forward = new Vector3D();
		_left = new Vector3D();

		_phi = 0.0;
		_theta = 0.0;

		_speed = 0.04;
		_sensivity = 0.4;
		_keyboardSensivity = 4;

		forward = false;
		backward = false;
		strafeLeft = false;
		strafeRight = false;
	}

	public FreeFlyCamera(final double xPos, final double yPos, final double zPos, final double xLPos,
		final double yLPos, final double zLPos, final JOGLAWTGLRenderer renderer) {
		super(xPos, yPos, zPos, xLPos, yLPos, zLPos, renderer);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void vectorsFromAngles() {
		Vector3D up = new Vector3D(0.0f, 0.0f, 1.0f);

		if ( _phi > 89 ) {
			_phi = 89;
		} else if ( _phi < -89 ) {
			_phi = -89;
		}

		double r_temp = Math.cos(_phi * Math.PI / 180.f);
		_forward.z = Math.sin(_phi * Math.PI / 180.f);
		_forward.x = r_temp * Math.cos(_theta * Math.PI / 180.f);
		_forward.y = r_temp * Math.sin(_theta * Math.PI / 180.f);

		_left = Vector3D.crossProduct(up, _forward);
		_left.normalize();

		// calculate the target of the camera
		_target = _forward.add(_position.x, _position.y, _position.z);

	}

	@Override
	public void animate() {
		if ( this.forward ) {
			if ( shiftKeyDown ) {
				_phi -= -_keyboardSensivity * _sensivity;
				vectorsFromAngles();
			} else {
				_position = _position.add(_forward.scalarMultiply(_speed * 200)); // go forward
			}
		}
		if ( this.backward ) {
			if ( shiftKeyDown ) {
				_phi -= _keyboardSensivity * _sensivity;
				vectorsFromAngles();
			} else {
				_position = _position.subtract(_forward.scalarMultiply(_speed * 200)); // go backward
			}
		}
		if ( this.strafeLeft ) {
			if ( shiftKeyDown ) {
				_theta -= -_keyboardSensivity * _sensivity;
				vectorsFromAngles();
			} else {
				_position = _position.add(_left.scalarMultiply(_speed * 200)); // move on the right
			}
		}
		if ( this.strafeRight ) {
			if ( shiftKeyDown ) {
				_theta -= _keyboardSensivity * _sensivity;
				vectorsFromAngles();
			} else {
				_position = _position.subtract(_left.scalarMultiply(_speed * 200)); // move on the left
			}
		}

		_target = _position.add(_forward.x, _forward.y, _forward.z);
	}

	@Override
	public void UpdateCamera(final GL gl, final GLU glu, final int width, int height) {
		if ( height == 0 ) {
			height = 1; // prevent divide by zero
		}
		float aspect = (float) width / height;

		glu.gluPerspective(45.0f, aspect, 0.1f, getMaxDim() * 100);

		glu.gluLookAt(_position.x, _position.y, _position.z, _target.x, _target.y, _target.z, 0.0f, 0.0f, 1.0f);
		animate();
	}

	public void followAgent(final IAgent a, final GLU glu) {
		ILocation l = a.getLocation();
		_position.x = l.getX();
		_position.y = l.getY();
		_position.z = l.getZ();
		glu.gluLookAt(0, 0, (float) (maxDim * 1.5), 0, 0, 0, 0.0f, 0.0f, 1.0f);
	}

	@Override
	public void initializeCamera(final double envWidth, final double envHeight) {
		if ( envWidth > envHeight ) {
			maxDim = envWidth;
		} else {
			maxDim = envHeight;
		}

		_position.x = envWidth / 2;
		_target.x = envWidth / 2;
		_position.y = -envHeight * 1.75;
		_target.y = -envHeight * 0.5;
		_position.z = getMaxDim();
		_target.z = 0;

		_phi = -45;
		_theta = 90;
		vectorsFromAngles();
	}

	@Override
	public void initialize3DCamera(final double envWidth, final double envHeight) {
		if ( envWidth > envHeight ) {
			maxDim = envWidth;
		} else {
			maxDim = envHeight;
		}

		_position.x = envWidth / 2;
		_target.x = envWidth / 2;
		_position.y = -envHeight * 1.75;
		_target.y = -envHeight * 0.5;
		_position.z = getMaxDim();
		_target.z = 0;

		_phi = -45;
		_theta = 90;
		vectorsFromAngles();
	}

	@Override
	public Vector3D getForward() {
		return this._forward;
	}

	@Override
	public Double getSpeed() {
		return this._speed;
	}

	@Override
	public void setZPosition(final double z) {
		this._position.z = z;
	}

	@Override
	public void mouseWheelMoved(final MouseWheelEvent arg0) {

		float incrementalZoomStep;
		// Check if Z is not equal to 0 (avoid being block on z=0)
		if ( _position.getZ() != 0 ) {
			incrementalZoomStep = (float) _position.getZ() / 10;
		} else {
			incrementalZoomStep = 0.1f;
		}
		if ( arg0.getWheelRotation() > 0 ) {
			_position = _position.subtract(_forward.scalarMultiply(_speed * 800 + Math.abs(incrementalZoomStep))); // on
																													// recule
			myRenderer.displaySurface.setZoomLevel(myRenderer.camera.getMaxDim() * INIT_Z_FACTOR / _position.getZ());
			_target = _forward.add(_position.x, _position.y, _position.z); // comme on a boug�, on recalcule la cible
																			// fix�e par la cam�ra
		} else {
			_position = _position.add(_forward.scalarMultiply(_speed * 800 + Math.abs(incrementalZoomStep))); // on
																												// avance
			myRenderer.displaySurface.setZoomLevel(myRenderer.camera.getMaxDim() * INIT_Z_FACTOR / _position.getZ());
			_target = _forward.add(_position.x, _position.y, _position.z); // comme on a boug�, on recalcule la cible
																			// fix�e par la cam�ra
		}
	}

	@Override
	public void mouseDragged(final MouseEvent arg0) {
		if ( (arg0.isShiftDown() || arg0.isAltDown()) && IsViewIn2DPlan()) {
			mousePosition.x = arg0.getX();
			mousePosition.y = arg0.getY();
			enableROIDrawing = true;
			myRenderer.DrawROI();
		} else {
			// check the difference between the current x and the last x position
			int horizMovement = arg0.getX() - lastxPressed;
			// check the difference between the current y and the last y position
			int vertMovement = arg0.getY() - lastyPressed;

			// set lastx to the current x position
			lastxPressed = arg0.getX();
			// set lastyPressed to the current y position
			lastyPressed = arg0.getY();

			_theta -= horizMovement * _sensivity;
			_phi -= vertMovement * _sensivity;

			vectorsFromAngles();
		}
	}

	@Override
	public void mouseClicked(final MouseEvent arg0) {
		if(arg0.getClickCount() > 1){
			myRenderer.displaySurface.zoomFit();
		}
		if ( (arg0.isShiftDown() || arg0.isAltDown())) {
			Point point = myRenderer.getIntWorldPointFromWindowPoint(new Point(arg0.getX(), arg0.getY()));

			mousePosition.x = arg0.getX();
			mousePosition.y = arg0.getY();
			enableROIDrawing = true;
			myRenderer.DrawROI();
			myRenderer.roiCenter.setLocation(point.x, point.y);

			enableROIDrawing = false;
		}
	}

	@Override
	public void mouseEntered(final MouseEvent arg0) {
		// if ( isArcBallOn(arg0) && isModelCentered ) {
		// if (arg0.getButton() ==3) {
		// myRenderer.reset();
		// }
		// } else {
		// // myCamera.PrintParam();
		// // System.out.println( "x:" + mouseEvent.getX() + " y:" + mouseEvent.getY());
		// }
	}

	@Override
	public void mouseExited(final MouseEvent arg0) {}

	@Override
	public void mousePressed(final MouseEvent arg0) {
		lastxPressed = arg0.getX();
		lastyPressed = arg0.getY();

		// Picking mode
		// if ( myRenderer.displaySurface.picking ) {
		// Activate Picking when press and right click and if in Picking mode
		if ( arg0.getButton() == 3 ) {
			isPickedPressed = true;
			myRenderer.setPicking(true);
			// myRenderer.drawPickableObjects();
		} else {
			myRenderer.setPicking(false);
			// }

		}

		mousePosition.x = arg0.getX();
		mousePosition.y = arg0.getY();

		myRenderer.getIntWorldPointFromWindowPoint(new Point(arg0.getX(), arg0.getY()));

	}

	@Override
	public void mouseReleased(final MouseEvent arg0) {
		
		if ((arg0.isShiftDown() || arg0.isAltDown()) && IsViewIn2DPlan() && enableROIDrawing == true){
			GamaPoint p = new GamaPoint(myRenderer.worldCoordinates.x, -myRenderer.worldCoordinates.y, 0.0);
			if ( arg0.isAltDown() ) {
				Iterator<IShape> shapes =
					GAMA.getSimulation()
						.getTopology()
						.getSpatialIndex()
						.allInEnvelope(
							new GamaPoint(myRenderer.roiCenter.x, -myRenderer.roiCenter.y),
							new Envelope(myRenderer.roi_List.get(0), myRenderer.roi_List.get(2), -myRenderer.roi_List
								.get(1), -myRenderer.roi_List.get(3)), new Different(), true);
				final Iterator<IAgent> agents = AbstractTopology.toAgents(shapes);
				myRenderer.displaySurface.selectSeveralAgents(agents, 0);				
			} 
			if(arg0.isShiftDown()){	
				myRenderer.ROIZoom();
			}
			enableROIDrawing = false;
		}
	}

	@Override
	public void keyPressed(final KeyEvent arg0) {
		switch (arg0.getKeyCode()) {
			case VK_LEFT:
				strafeLeft = true;
				shiftKeyDown = checkShiftKeyDown(arg0);
				break;
			case VK_RIGHT:
				strafeRight = true;
				shiftKeyDown = checkShiftKeyDown(arg0);
				break;
			case VK_UP:
				forward = true;
				shiftKeyDown = checkShiftKeyDown(arg0);
				break;
			case VK_DOWN:
				backward = true;
				shiftKeyDown = checkShiftKeyDown(arg0);
				break;
		}
	}

	@Override
	public void keyReleased(final KeyEvent arg0) {
		switch (arg0.getKeyCode()) {
			case VK_LEFT: // player turns left (scene rotates right)
				strafeLeft = false;
				break;
			case VK_RIGHT: // player turns right (scene rotates left)
				strafeRight = false;
				break;
			case VK_UP:
				forward = false;
				break;
			case VK_DOWN:
				backward = false;
				break;
		}
	}

	@Override
	public void keyTyped(final KeyEvent arg0) {}

	@Override
	public double getMaxDim() {
		return maxDim;
	}

	@Override
	public void PrintParam() {
		System.out.println("xPos:" + _position.x + " yPos:" + _position.y + " zPos:" + _position.z);
		System.out.println("xLPos:" + _target.x + " yLPos:" + _target.y + " zLPos:" + _target.z);
		System.out.println("_forwardX:" + _forward.x + " _forwardY:" + _forward.y + " _forwardZ:" + _forward.z);
		System.out.println("_phi : " + _phi + " _theta : " + _theta);

	}

	@Override
	public boolean IsViewIn2DPlan() {
		if ( _phi >= -89 && _phi < -85 ) {
			return true;
		} else {
			return false;
		}

	}

}
