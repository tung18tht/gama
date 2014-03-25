/*
 * GAMA - V1.4 http://gama-platform.googlecode.com
 * 
 * (c) 2007-2011 UMI 209 UMMISCO IRD/UPMC & Partners (see below)
 * 
 * Developers :
 * 
 * - Alexis Drogoul, UMI 209 UMMISCO, IRD/UPMC (Kernel, Metamodel, GAML), 2007-2012
 * - Vo Duc An, UMI 209 UMMISCO, IRD/UPMC (SWT, multi-level architecture), 2008-2012
 * - Patrick Taillandier, UMR 6228 IDEES, CNRS/Univ. Rouen (Batch, GeoTools & JTS), 2009-2012
 * - Beno�t Gaudou, UMR 5505 IRIT, CNRS/Univ. Toulouse 1 (Documentation, Tests), 2010-2012
 * - Phan Huy Cuong, DREAM team, Univ. Can Tho (XText-based GAML), 2012
 * - Pierrick Koch, UMI 209 UMMISCO, IRD/UPMC (XText-based GAML), 2010-2011
 * - Romain Lavaud, UMI 209 UMMISCO, IRD/UPMC (RCP environment), 2010
 * - Francois Sempe, UMI 209 UMMISCO, IRD/UPMC (EMF model, Batch), 2007-2009
 * - Edouard Amouroux, UMI 209 UMMISCO, IRD/UPMC (C++ initial porting), 2007-2008
 * - Chu Thanh Quang, UMI 209 UMMISCO, IRD/UPMC (OpenMap integration), 2007-2008
 */
package msi.gama.common.util;

import java.util.*;
import msi.gama.common.interfaces.*;
import msi.gama.kernel.experiment.IExperimentSpecies;
import msi.gama.kernel.simulation.SimulationAgent;
import msi.gama.metamodel.agent.IAgent;
import msi.gama.outputs.*;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gaml.architecture.user.UserPanelStatement;
import msi.gaml.types.IType;
import org.eclipse.core.runtime.CoreException;

/**
 * The class GuiUtils. A static bridge to the SWT environment. The actual dependency on SWT is
 * represented by an instance of IGui, which must be initialize when the UI plugin launches.
 * 
 * @author drogoul
 * @since 18 dec. 2011
 * 
 */
public class GuiUtils {

	static IGui gui;

	public static final String MONITOR_VIEW_ID = "msi.gama.application.view.MonitorView";
	public static final String AGENT_VIEW_ID = "msi.gama.application.view.AgentInspectView";
	public static final String TABLE_VIEW_ID = "msi.gama.application.view.TableAgentInspectView";
	public static final String LAYER_VIEW_ID = "msi.gama.application.view.LayeredDisplayView";
	public static final String WEB_VIEW_ID = "msi.gama.application.view.WebDisplayView";
	public static final String ERROR_VIEW_ID = "msi.gama.application.view.ErrorView";
	public static final String PARAMETER_VIEW_ID = "msi.gama.application.view.ParameterView";

	public static final String GRAPHSTREAM_VIEW_ID = "msi.gama.networks.ui.GraphstreamView";
	public static final String HPC_PERSPECTIVE_ID = "msi.gama.hpc.HPCPerspectiveFactory";

	private static boolean headlessMode = false;

	public static boolean isInHeadLessMode() {
		return headlessMode;
	}

	/**
	 * Method called by headless builder to change the GUI Mode
	 * @see ModelFactory
	 */

	public static void cycleDisplayViews(final Set<String> names) {
		if ( gui != null ) {
			gui.cycleDisplayViews(names);
		}
	}

	public static void setHeadLessMode() {
		headlessMode = true;
	}

	public static void setGUIMode() {
		headlessMode = false;
	}

	/**
	 * Method called by the UI plugin to initialize the SWT environment to talk with.
	 * @param gui an instance of IGui
	 */
	public static void setSwtGui(final IGui gui) {
		GuiUtils.gui = gui;
	}

	public static void waitStatus(final String string) {
		if ( gui != null ) {
			gui.setStatus(string, IGui.WAIT);
		}
	}

	public static void informStatus(final String string) {
		if ( gui != null ) {
			gui.setStatus(string, IGui.INFORM);
		} else {
			System.out.println(string);
		}
	}

	public static void errorStatus(final String error) {
		if ( gui != null ) {
			gui.setStatus(error, IGui.ERROR);
		} else {
			System.out.println("Status:" + error);
		}
	}

	public static void neutralStatus(final String message) {
		if ( gui != null ) {
			gui.setStatus(message, IGui.NEUTRAL);
		} else {
			System.out.println("Status:" + message);
		}
	}

	/**
	 * @param abstractDisplayOutput
	 * @param refresh
	 */
	public static void setViewRateOf(final IDisplayOutput abstractDisplayOutput, final int refresh) {}

	/**
	 * 
	 * See IWorkbenchConstant.VIEW_XXX for the code
	 * @param viewId
	 * @param string
	 * @return
	 */
	public static IGamaView showView(final String viewId, final String name, final int code) {
		if ( gui != null ) { return gui.showView(viewId, name, code); }
		return null;
	}

	/**
	 * @param ex
	 */
	public static void raise(final Throwable ex) {
		if ( gui != null ) {
			gui.raise(ex);
		} else {
			ex.printStackTrace();
		}
	}

	public static void error(final String error) {
		if ( gui != null ) {
			gui.error(error);
		} else {
			System.out.println(error);
		}
	}

	public static void tell(final String message) {
		if ( gui != null ) {
			gui.tell(message);
		} else {
			System.out.println(message);
		}
	}

	public static void asyncRun(final Runnable block) {
		if ( gui != null ) {
			gui.asyncRun(block);
		} else {
			block.run();
		}
	}

	public static void showParameterView(final IExperimentSpecies exp) {
		if ( gui != null ) {
			gui.showParameterView(exp);
		}
	}

	public static void informConsole(final String s) {
		if ( gui != null ) {
			gui.informConsole(s);
		} else {
			System.out.println(s);
		}
	}

	/**
	 * @param cycle
	 * @param s
	 */
	public static void debugConsole(final int cycle, final String s) {
		if ( gui != null ) {
			gui.debugConsole(cycle, s);
		} else {
			System.out.println(s);
		}
	}

	public static void run(final Runnable block) {
		if ( gui != null ) {
			gui.run(block);
		} else {
			block.run();
		}
	}

	public static void updateViewOf(final IDisplayOutput output) {
		if ( gui != null ) {
			gui.updateViewOf(output);
		}
	}

	public static void warn(final String string) {
		if ( gui != null ) {
			gui.warn(string);
		} else {
			System.out.println(string);
		}
	}

	public static void debug() {
		debug("Breakpoint to remove");
		Thread.dumpStack();
	}

	public static void debug(final String string) {
		if ( gui != null ) {
			gui.debug(string);
		} else {
			System.out.println(string);
		}
	}

	public static void runtimeError(final GamaRuntimeException g) {
		if ( gui != null ) {
			gui.runtimeError(g);
		} else {
			System.out.println(g.getMessage());
		}
	}

	public static IEditorFactory getEditorFactory() {
		if ( gui != null ) { return gui.getEditorFactory(); }
		return null;
	}

	public static boolean confirmClose(final IExperimentSpecies experiment) {
		if ( gui != null ) { return gui.confirmClose(experiment); }
		return true;
	}

	public static void prepareForExperiment(final IExperimentSpecies exp) {
		if ( gui != null ) {
			gui.prepareForExperiment(exp);
		}
	}

	public static void prepareForSimulation(final SimulationAgent agent) {
		if ( gui != null ) {
			gui.prepareForSimulation(agent);
		}
	}

	public static void cleanAfterExperiment(final IExperimentSpecies exp) {
		if ( gui != null ) {
			gui.cleanAfterExperiment(exp);
		}
	}

	public static void cleanAfterSimulation() {
		if ( gui != null ) {
			gui.cleanAfterSimulation();
		}
	}

	public static void showConsoleView() {
		if ( gui != null ) {
			gui.showConsoleView();
		}
	}

	// public static void hideMonitorView() {
	//
	// if ( gui != null ) {
	// gui.hideMonitorView();
	// }
	//
	// }

	public static void setWorkbenchWindowTitle(final String string) {
		if ( gui != null ) {
			gui.setWorkbenchWindowTitle(string);
		}
	}

	public static void closeViewOf(final IDisplayOutput out) {
		if ( gui != null ) {
			gui.closeViewOf(out);
		}
	}

	public static void hideView(final String viewId) {
		if ( gui != null ) {
			gui.hideView(viewId);
		}
	}

	public static boolean isModelingPerspective() {
		return gui == null ? false : gui.isModelingPerspective();
	}

	public static void openModelingPerspective() {
		if ( gui != null ) {
			gui.openModelingPerspective();
		}
	}

	public static boolean isSimulationPerspective() {
		return gui == null ? false : gui.isSimulationPerspective();
	}

	public static IGui getGui() {
		return gui;
	}

	public static void togglePerspective() {
		if ( gui != null ) {
			gui.togglePerspective();
		}
	}

	public static void openSimulationPerspective() {
		if ( gui != null ) {
			gui.openSimulationPerspective();
		}
	}

	//
	// /**
	// * @param newWidth
	// * @param newHeight
	// * @return
	// */
	// public static IGraphics newGraphics(final int width, final int height) {
	// return gui != null ? gui.newGraphics(width, height) : null;
	// }

	/**
	 * @param layerDisplayOutput
	 * @param w
	 * @param h
	 * @return
	 */
	public static IDisplaySurface getDisplaySurfaceFor(final String keyword,
		final LayeredDisplayOutput layerDisplayOutput, final double w, final double h, final Object ... args) {
		return gui != null ? gui.getDisplaySurfaceFor(keyword, layerDisplayOutput, w, h, args) : null;
	}

	public static Map<String, Object> openUserInputDialog(final String title, final Map<String, Object> initialValues,
		final Map<String, IType> types) {
		if ( gui == null ) { return initialValues; }
		return gui.openUserInputDialog(title, initialValues, types);
	}

	public static void openUserControlPanel(final IScope scope, final UserPanelStatement panel) {
		if ( gui == null ) { return; }
		gui.openUserControlPanel(scope, panel);
	}

	public static void closeDialogs() {
		if ( gui == null ) { return; }
		gui.closeDialogs();
	}

	// TODO Transform this into a list
	public static IAgent getHighlightedAgent() {
		if ( gui == null ) { return null; }
		return gui.getHighlightedAgent();
	}

	public static void setHighlightedAgent(final IAgent a) {
		if ( gui == null ) { return; }
		gui.setHighlightedAgent(a);
	}

	public static void setSelectedAgent(final IAgent a) {
		if ( gui == null ) { return; }
		gui.setSelectedAgent(a);
	}

	public static void editModel(final Object eObject) {
		if ( gui == null ) { return; }
		gui.editModel(eObject);
	}

	public static void runModel(final Object object, final String exp) {
		if ( gui == null ) { return; }
		try {
			gui.runModel(object, exp);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	public static void updateParameterView(final IExperimentSpecies exp) {
		if ( gui == null ) { return; }
		gui.updateParameterView(exp);
	}

	public static void waitForViewsToBeInitialized() {
		if ( gui == null ) { return; }
		gui.waitForViewsToBeInitialized();
	}

	/**
	 * @param e
	 */
	public static void debug(final Exception e) {
		if ( gui == null ) {
			e.printStackTrace();
		} else {
			gui.debug(e);
		}
	}

	/**
	 * @return
	 */
	public static IDisplaySurface getFirstDisplaySurface() {
		return gui.getFirstDisplaySurface();
	}

}
