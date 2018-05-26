/*
 * Copyright 2018 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor.io;

import pixelitor.Composition;
import pixelitor.gui.GlobalKeyboardWatch;
import pixelitor.gui.PixelitorWindow;
import pixelitor.gui.utils.ImagePreviewPanel;
import pixelitor.gui.utils.SaveFileChooser;
import pixelitor.utils.Messages;
import pixelitor.utils.ProgressPanel;
import pixelitor.utils.Utils;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.io.File;

/**
 * Utility class with static methods related to file choosers
 */
public class FileChoosers {
    private static JFileChooser openChooser;
    private static SaveFileChooser saveChooser;

    public static final FileFilter jpegFilter = new FileNameExtensionFilter("JPEG files", "jpg", "jpeg");
    private static final FileFilter pngFilter = new FileNameExtensionFilter("PNG files", "png");
    private static final FileFilter bmpFilter = new FileNameExtensionFilter("BMP files", "bmp");
    public static final FileNameExtensionFilter gifFilter = new FileNameExtensionFilter("GIF files", "gif");
    private static final FileFilter tiffFilter = new FileNameExtensionFilter("TIFF files", "tiff", "tif");
    private static final FileFilter pxcFilter = new FileNameExtensionFilter("PXC files", "pxc");
    public static final FileFilter oraFilter = new FileNameExtensionFilter("OpenRaster files", "ora");

    private static final FileFilter[] OPEN_SAVE_FILTERS;

    static {
        if (Utils.getCurrentMainJavaVersion() == 8) {
            OPEN_SAVE_FILTERS = new FileFilter[]{bmpFilter, gifFilter, jpegFilter, oraFilter, pngFilter, pxcFilter};
        } else {
            OPEN_SAVE_FILTERS = new FileFilter[]{bmpFilter, gifFilter, jpegFilter, oraFilter, pngFilter, pxcFilter, tiffFilter};
        }
    }


    private FileChoosers() {
    }

    private static void initOpenChooser() {
        assert SwingUtilities.isEventDispatchThread() : "not EDT thread";

        if (openChooser == null) {
            //noinspection NonThreadSafeLazyInitialization
            openChooser = new JFileChooser(Directories.getLastOpenDir());
            openChooser.setName("open");

            setDefaultOpenExtensions();

            JPanel p = new JPanel();
            p.setLayout(new BorderLayout());
            ProgressPanel progressPanel = new ProgressPanel();
            ImagePreviewPanel preview = new ImagePreviewPanel(progressPanel);
            p.add(preview, BorderLayout.CENTER);
            p.add(progressPanel, BorderLayout.SOUTH);

            openChooser.setAccessory(p);
            openChooser.addPropertyChangeListener(preview);
        }
    }

    public static void initSaveChooser() {
        assert SwingUtilities.isEventDispatchThread() : "not EDT thread";

        if (saveChooser == null) {
            //noinspection NonThreadSafeLazyInitialization
            saveChooser = new SaveFileChooser(Directories.getLastSaveDir());
            saveChooser.setName("save");
            saveChooser.setDialogTitle("Save As");

            setDefaultSaveExtensions();
        }
    }

    public static void open() {
        initOpenChooser();

        GlobalKeyboardWatch.setDialogActive(true);
        int status = openChooser.showOpenDialog(PixelitorWindow.getInstance());
        GlobalKeyboardWatch.setDialogActive(false);

        if (status == JFileChooser.APPROVE_OPTION) {
            File selectedFile = openChooser.getSelectedFile();
            String fileName = selectedFile.getName();

            Directories.setLastOpenDir(selectedFile.getParentFile());

            if (FileExtensionUtils.hasSupportedInputExt(fileName)) {
                OpenSaveManager.openFile(selectedFile);
            } else { // unsupported extension
                handleUnsupportedExtensionWhileOpening(fileName);
            }
        } else if (status == JFileChooser.CANCEL_OPTION) {
            // cancelled
        }
    }

    private static void handleUnsupportedExtensionWhileOpening(String fileName) {
        String extension = FileExtensionUtils.getExt(fileName);
        String msg = "Could not open " + fileName + ", because ";
        if (extension == null) {
            msg += "it has no extension.";
        } else {
            msg += "files of type " + extension + " are not supported.";
        }
        Messages.showError("Error", msg);
    }

    public static boolean showSaveChooserAndSaveComp(Composition comp) {
        String defaultFileName = FileExtensionUtils.stripExtension(comp.getName());
        saveChooser.setSelectedFile(new File(defaultFileName));

        File customSaveDir = null;
        File file = comp.getFile();
        if (file != null) {
            customSaveDir = file.getParentFile();
            saveChooser.setCurrentDirectory(customSaveDir);
        }

        GlobalKeyboardWatch.setDialogActive(true);
        int status = saveChooser.showSaveDialog(PixelitorWindow.getInstance());
        GlobalKeyboardWatch.setDialogActive(false);

        if (status == JFileChooser.APPROVE_OPTION) {
            File selectedFile = saveChooser.getSelectedFile();

            if (customSaveDir == null) {
                // if the comp had no file, and lastSaveDir was used,
                // then update lastSaveDir
                Directories.setLastSaveDir(selectedFile.getParentFile());
            } else {
                // if a custom save directory (the file dir) was used,
                // reset the directory stored inside the chooser
                saveChooser.setCurrentDirectory(Directories.getLastSaveDir());
            }

            String extension = saveChooser.getExtension();
            OutputFormat outputFormat = OutputFormat.fromExtension(extension);
            outputFormat.saveComp(comp, selectedFile, true);
            return true;
        }

        return false;
    }

    /**
     * Returns true if the file was saved, false if the user cancels the saving
     */
    public static boolean saveWithChooser(Composition comp) {
        initSaveChooser();

        String defaultExt = FileExtensionUtils.getExt(comp.getName());
        saveChooser.setFileFilter(getFileFilterForExtension(defaultExt));

        return showSaveChooserAndSaveComp(comp);
    }

    private static FileFilter getFileFilterForExtension(String ext) {
        if(ext == null) {
            return jpegFilter; // default
        }
        ext = ext.toLowerCase();
        switch (ext) {
            case "jpg":
            case "jpeg":
                return jpegFilter;
            case "png":
                return pngFilter;
            case "bmp":
                return bmpFilter;
            case "gif":
                return gifFilter;
            case "pxc":
                return pxcFilter;
            case "tif":
            case "tiff":
                return tiffFilter;
        }
        return jpegFilter; // default
    }

    private static void setDefaultOpenExtensions() {
        addDefaultFilters(openChooser);
    }

    public static void setDefaultSaveExtensions() {
        addDefaultFilters(saveChooser);
    }

    public static void setOnlyOneSaveExtension(FileFilter filter) {
        setupFilterToOnlyOneFormat(saveChooser, filter);
    }

    public static void setOnlyOneOpenExtension(FileFilter filter) {
        setupFilterToOnlyOneFormat(saveChooser, filter);
    }

    private static void addDefaultFilters(JFileChooser chooser) {
        for (FileFilter filter : OPEN_SAVE_FILTERS) {
            chooser.addChoosableFileFilter(filter);
        }
    }

    private static void setupFilterToOnlyOneFormat(JFileChooser chooser, FileFilter chosenFilter) {
        for (FileFilter filter : OPEN_SAVE_FILTERS) {
            if(filter != chosenFilter) {
                chooser.removeChoosableFileFilter(filter);
            }
        }

        chooser.setFileFilter(chosenFilter);
    }

    public static File selectSaveFileForSpecificFormat(FileFilter fileFilter) {
        File selectedFile = null;
        try {
            initSaveChooser();
            setupFilterToOnlyOneFormat(saveChooser, fileFilter);

            GlobalKeyboardWatch.setDialogActive(true);
            int status = saveChooser.showSaveDialog(PixelitorWindow.getInstance());
            GlobalKeyboardWatch.setDialogActive(false);

            if (status == JFileChooser.APPROVE_OPTION) {
                selectedFile = saveChooser.getSelectedFile();
                Directories.setLastSaveDir(selectedFile.getParentFile());
            }
            if (status == JFileChooser.CANCEL_OPTION) {
                // save cancelled
                return null;
            }
            return selectedFile;
        } finally {
            setDefaultSaveExtensions();
        }
    }

}
