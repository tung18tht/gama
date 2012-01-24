/**
 * Created by drogoul, 20 d�c. 2011
 * 
 */
package msi.gama.common.util;

import java.io.*;
import msi.gama.common.interfaces.IFileAccess;
import msi.gama.precompiler.GamlProperties;
import msi.gaml.compilation.GamlException;
import org.osgi.framework.Bundle;

/**
 * The class FileUtils.
 * 
 * @author drogoul
 * @since 20 d�c. 2011
 * 
 */
public class FileUtils {

	static IFileAccess fileAccess;

	public static void setFileAccess(final IFileAccess fa) {
		fileAccess = fa;
	}

	public static GamlProperties getGamaProperties(final Bundle pluginName,
		final String pathToAdditions, final String fileName) throws GamlException {
		if ( fileAccess == null ) { throw new GamlException("Properties " + fileName +
			" not accessible"); }
		return fileAccess.getGamaProperties(pluginName, pathToAdditions, fileName);
	}

	/**
	 * Checks if is absolute path.
	 * 
	 * @param filePath the file path
	 * 
	 * @return true, if is absolute path
	 */
	static boolean isAbsolutePath(final String filePath) {
		final File[] roots = File.listRoots();
		for ( int i = 0; i < roots.length; i++ ) {
			if ( filePath.startsWith(roots[i].getAbsolutePath()) ) { return true; }
		}
		return false;
	}

	/**
	 * Removes a root.
	 * 
	 * @param absoluteFilePath the absolute file path
	 * 
	 * @return the string
	 */
	public static String removeRoot(final String absoluteFilePath) {
		// OutputManager.debug("absoluteFilePath before = " + absoluteFilePath);

		final File[] roots = File.listRoots();
		for ( int i = 0; i < roots.length; i++ ) {
			if ( absoluteFilePath.startsWith(roots[i].getAbsolutePath()) ) { return absoluteFilePath
				.substring(roots[i].getAbsolutePath().length(), absoluteFilePath.length()); }
		}
		return absoluteFilePath;
	}

	/**
	 * Construct absolute file path.
	 * 
	 * @param filePath the file path
	 * @param mustExist the must exist
	 * 
	 * @return the string
	 * 
	 * @throws GamlException the gaml exception
	 */
	static public String constructAbsoluteFilePath(final String filePath,
		final String referenceFile, final boolean mustExist) throws GamlException {
		String baseDirectory = new File(referenceFile).getParent();
		final GamlException ex =
			new GamlException("File denoted by " + filePath +
				" not found! Tried the following paths : ");
		File file = null;
		if ( isAbsolutePath(filePath) ) {
			file = new File(filePath);
			if ( file.exists() || !mustExist ) {
				try {
					return file.getCanonicalPath();
				} catch (final IOException e) {
					e.printStackTrace();
					return file.getAbsolutePath();
				}
			}
			ex.addContext(file.getAbsolutePath());
			file = new File(baseDirectory + File.separator + removeRoot(filePath));

			if ( file.exists() ) {
				try {
					return file.getCanonicalPath();
				} catch (final IOException e) {
					e.printStackTrace();
					return file.getAbsolutePath();
				}
			}
			ex.addContext(file.getAbsolutePath());
		} else {
			file = new File(baseDirectory + File.separatorChar + filePath);
			if ( file.exists() || !mustExist ) {
				try {
					return file.getCanonicalPath();
				} catch (final IOException e) {
					e.printStackTrace();
					return file.getAbsolutePath();
				}
			}
			ex.addContext(file.getAbsolutePath());
		}

		throw ex;
	}

}
