/*
 * This file is part of WiFi101 Updater Arduino-IDE Plugin.
 * Copyright 2016 Arduino LLC (http://www.arduino.cc/)
 *
 * Arduino is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * As a special exception, you may use this file as part of a free software
 * library without restriction.  Specifically, if other files instantiate
 * templates or use macros or inline functions from this file, or you compile
 * this file and link it with other files to produce an executable, this
 * file does not by itself cause the resulting executable to be covered by
 * the GNU General Public License.  This exception does not however
 * invalidate any other reasons why the executable file might be covered by
 * the GNU General Public License.
 */
package cc.arduino.plugins.wifi101.flashers;

import static processing.app.I18n.tr;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JProgressBar;

import org.apache.commons.lang3.StringUtils;

import cc.arduino.Compiler;
import cc.arduino.UploaderUtils;
import cc.arduino.packages.BoardPort;
import cc.arduino.plugins.wifi101.UpdaterImpl;
import cc.arduino.plugins.wifi101.UpdaterJFrame;
import cc.arduino.plugins.wifi101.flashers.java.FlasherSerialClient;
import processing.app.Base;
import processing.app.BaseNoGui;
import processing.app.I18n;
import processing.app.PreferencesData;
import processing.app.Sketch;
import processing.app.debug.TargetBoard;
import processing.app.debug.TargetPackage;
import processing.app.debug.TargetPlatform;
import processing.app.packages.LibraryList;
import processing.app.packages.UserLibrary;

public class Flasher {

	public String modulename;
	public String version;
	public File file;
	public JProgressBar progressBar;
	public String name;
	public String filename;
	public List<String> compatibleBoard;
	public boolean certavail;
	protected int baudrate;

	public Flasher() {}

	public Flasher(String _modulename, String _version, String _filename, boolean _certavail, int _baudrate, ArrayList<String> _compatibleBoard) {
		modulename = _modulename;
		compatibleBoard = new ArrayList<String>();
		version = _version;
		file = null;
		name = "NINA";
		certavail = _certavail;
		compatibleBoard.addAll(_compatibleBoard);
		filename = _filename;
		baudrate = _baudrate;
	}

	public void progress(int progress, String text) {
		if (text.length() > 60) {
			text = text.substring(0, 60) + "...";
		}
		progressBar.setValue(progress);
		progressBar.setStringPainted(true);
		progressBar.setString(text);
	}

	public void testConnection(String port, int baudrate) throws Exception {
		FlasherSerialClient client = null;
		try {
			progress(50, "Testing programmer...");
			client = new FlasherSerialClient();
			client.open(port, baudrate);
			client.hello();
			progress(100, "Done!");
		} finally {
			if (client != null) {
				client.close();
			}
		}
	}

	public String findFirmwareUpdaterExamplePath() {
		LibraryList allLibraries = BaseNoGui.librariesIndexer.getInstalledLibraries();
		String firmwareUpdaterExamplePath = "";
		String nameToSearchFor = "";
		String pathToSketch = "";
		if (modulename.contains("NINA")) {
			nameToSearchFor = "WiFiNINA";
			pathToSketch = "examples/Tools/FirmwareUpdater/FirmwareUpdater.ino";
		}
		if (modulename.contains("WINC")) {
			nameToSearchFor = "WiFi101";
			pathToSketch = "examples/FirmwareUpdater/FirmwareUpdater.ino";
		}
		for (UserLibrary lib : allLibraries) {
		  if (lib.getName().equals(nameToSearchFor)) {
			  firmwareUpdaterExamplePath = lib.getInstalledFolder().getAbsolutePath() + "/" + pathToSketch;
		  }
		}
		return firmwareUpdaterExamplePath;
	}

	public TargetBoard getBoard(BoardPort port) {
		String name = port.getBoardName();
		if (name == null) {
			return null;
		}
		TargetBoard targetBoard = null;
	    for (TargetPackage targetPackage : BaseNoGui.packages.values()) {
	      for (TargetPlatform targetPlatform : targetPackage.getPlatforms().values()) {
	        for (TargetBoard board : targetPlatform.getBoards().values()) {
	          if (name.equals(board.getName())) {
	            	targetBoard = board;
	          }
	        }
	      }
	    }
	    return targetBoard;
	}

	public void setBoardAndPort(BoardPort port) {
		
		// Search all packages for this f***ing name
		TargetBoard targetBoard = getBoard(port);
		BaseNoGui.selectSerialPort(port.getAddress());
		if (targetBoard != null) {
			BaseNoGui.selectBoard(targetBoard);
		}
		Base.INSTANCE.onBoardOrPortChange();
		
	}

	public void onFwUpdateFailure(BoardPort port) throws Exception {
		if (findFirmwareUpdaterExamplePath() != "") {
			if (getBoard(port) != null) {
				uploadFirmwareUpdaterSketch(port);
				// TODO: decide if we want to launch it here
				//t.wait();
				updateFirmware(port.getAddress());
			} else {
				openFirmwareUpdaterSketch(port);
			}
		}
	}

	public String onFwUpdateFailureStrings(BoardPort port) {
		if (findFirmwareUpdaterExamplePath() != "") {
			if (getBoard(port) != null) {
				return "retry after uploading the Updater sketch?\nThis will overwrite your existing sketch";
			} else {
				return "open the Updater sketch?";
			}
		}
		return "";
	}

	public void uploadFirmwareUpdaterSketch(BoardPort port) throws Exception {
		String firmwareUpdaterExamplePath = findFirmwareUpdaterExamplePath();
		if (firmwareUpdaterExamplePath != "" && port != null) {

			setBoardAndPort(port);
			setProgressBar(UpdaterImpl.getUpdateProgressBar());

//			Thread t = new Thread() {
//				public void run() {
					try {
						progress(10, "Compling file...");
			
						File sketchFile = BaseNoGui.absoluteFile(firmwareUpdaterExamplePath);
						Sketch sketch = new Sketch(sketchFile);
				        String outputFile = new Compiler(sketch).build(progress -> {}, false);
			
				        progress(50, "Uploading file...");
			
						UploaderUtils uploader = new UploaderUtils();
				        List<String> warnings = new ArrayList<>();
				        boolean res = uploader.upload(sketch, null, outputFile,	false, false, warnings);
			
				        progress(100, "Updater uploaded!");
					} catch (Exception e) {
						
					}
				}
//			};
//			t.start();
//			return t;
//		}
//		return null;
	}

	public void openFirmwareUpdaterSketch(BoardPort port) throws Exception {

		String firmwareUpdaterExamplePath = findFirmwareUpdaterExamplePath();
		if (firmwareUpdaterExamplePath != "" && port != null) {
			setBoardAndPort(port);
			Base.INSTANCE.handleOpen(new File(firmwareUpdaterExamplePath));
		}
	}

	public void updateFirmware(String port) throws Exception {
		// To be overridden
	}

	public void uploadCertificates(String port, List<String> websites) throws Exception {
		// To be overridden
	}

	public void setProgressBar(JProgressBar _progressBar) {
		progressBar = _progressBar;
	}

	public String getName() {
		return name;
	}

	public int getBaudrate() {
		return baudrate;
	}

	public byte[] getData() throws IOException {
		InputStream in = null;
		try {
			in = new FileInputStream(file);
			ByteArrayOutputStream res = new ByteArrayOutputStream();
			byte buff[] = new byte[4096];
			while (in.available() > 0) {
				int read = in.read(buff);
				if (read == -1) {
					break;
				}
				res.write(buff, 0, read);
			}
			return res.toByteArray();
		} finally {
			if (in != null) {
				in.close();
			}
		}
	}

	public File getFile() {
		return file;
	}

	public void setFileName(String _filename) {
		filename = _filename;
	}

	public boolean isCompatible(String boardName) {
		if (boardName == null) {
			return false;
		}
		for (String name : compatibleBoard) {
			if (name.toLowerCase().equals(boardName.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	public boolean certificatesAvailable() {
		return certavail;
	}

	public File openFirmwareFile() throws Exception {
		try {
			String jarPath = Flasher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			File jarFolder = new File(jarPath).getParentFile();
			File fwfile = new File(jarFolder, filename);
			return fwfile;
		} catch (URISyntaxException e) {
			String message = "File not found ";
			throw new Exception(message.concat(filename));
		}
	}

	public String toString() {
		String names = modulename + " (" + version + ") (";
		for (String lname : compatibleBoard) {
			names = names.concat(lname).concat(", ");
		}
		names = names.substring(0, (names.length() - 2)).concat(")");
		names = StringUtils.abbreviate(names, 75);
		return names;
	}
}
