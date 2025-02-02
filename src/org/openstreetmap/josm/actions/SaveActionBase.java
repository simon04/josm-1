// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import org.openstreetmap.josm.data.PreferencesUtils;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.io.importexport.FileExporter;
import org.openstreetmap.josm.gui.layer.AbstractModifiableLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.AbstractFileChooser;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

/**
 * Abstract superclass of save actions.
 * @since 290
 */
public abstract class SaveActionBase extends DiskAccessAction {

    private boolean quiet;

    /**
     * Constructs a new {@code SaveActionBase}.
     * @param name The action's text as displayed on the menu (if it is added to a menu)
     * @param iconName The filename of the icon to use
     * @param tooltip A longer description of the action that will be displayed in the tooltip
     * @param shortcut A ready-created shortcut object or {@code null} if you don't want a shortcut
     */
    protected SaveActionBase(String name, String iconName, String tooltip, Shortcut shortcut) {
        super(name, iconName, tooltip, shortcut);
    }

    /**
     * Constructs a new {@code SaveActionBase}.
     * @param name The action's text as displayed on the menu (if it is added to a menu)
     * @param iconName The filename of the icon to use
     * @param tooltip A longer description of the action that will be displayed in the tooltip
     * @param shortcut A ready-created shortcut object or {@code null} if you don't want a shortcut
     * @param quiet whether the quiet exporter is called
     * @since 15496
     */
    protected SaveActionBase(String name, String iconName, String tooltip, Shortcut shortcut, boolean quiet) {
        super(name, iconName, tooltip, shortcut);
        this.quiet = quiet;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isEnabled())
            return;
        doSave(quiet);
    }

    /**
     * Saves the active layer.
     * @return {@code true} if the save operation succeeds
     */
    public boolean doSave() {
        return doSave(false);
    }

    /**
     * Saves the active layer.
     * @param quiet If the file is saved without prompting the user
     * @return {@code true} if the save operation succeeds
     * @since 15496
     */
    public boolean doSave(boolean quiet) {
        Layer layer = getLayerManager().getActiveLayer();
        if (layer != null && layer.isSavable()) {
            return doSave(layer, quiet);
        }
        return false;
    }

    /**
     * Saves the given layer.
     * @param layer layer to save
     * @return {@code true} if the save operation succeeds
     */
    public boolean doSave(Layer layer) {
        return doSave(layer, false);
    }

    /**
     * Saves the given layer.
     * @param layer layer to save
     * @param quiet If the file is saved without prompting the user
     * @return {@code true} if the save operation succeeds
     * @since 15496
     */
    public boolean doSave(Layer layer, boolean quiet) {
        if (!layer.checkSaveConditions())
            return false;
        final boolean result = doInternalSave(layer, getFile(layer), quiet);
        updateEnabledState();
        return result;
    }

    /**
     * Saves a layer to a given file.
     * @param layer The layer to save
     * @param file The destination file
     * @param checkSaveConditions if {@code true}, checks preconditions before saving. Set it to {@code false} to skip it
     * and prevent dialogs from being shown.
     * @return {@code true} if the layer has been successfully saved, {@code false} otherwise
     * @since 7204
     */
    public static boolean doSave(Layer layer, File file, boolean checkSaveConditions) {
        if (checkSaveConditions && !layer.checkSaveConditions())
            return false;
        return doInternalSave(layer, file, !checkSaveConditions);
    }

    private static boolean doInternalSave(Layer layer, File file, boolean quiet) {
        if (file == null)
            return false;

        ImageIcon icon = ImageProvider.get("save");
        Notification savingNotification = new Notification(tr("Saving file {0}...", file.getName())).setIcon(icon);
        GuiHelper.runInEDT(savingNotification::show);
        try {
            boolean exported = false;
            boolean canceled = false;
            for (FileExporter exporter : ExtensionFileFilter.getExporters()) {
                if (exporter.acceptFile(file, layer)) {
                    if (quiet) {
                        exporter.exportDataQuiet(file, layer);
                    } else {
                        exporter.exportData(file, layer);
                    }
                    exported = true;
                    canceled = exporter.isCanceled();
                    break;
                }
            }
            if (!exported) {
                GuiHelper.runInEDT(() ->
                    JOptionPane.showMessageDialog(MainApplication.getMainFrame(), tr("No Exporter found! Nothing saved."), tr("Warning"),
                        JOptionPane.WARNING_MESSAGE));
                return false;
            } else if (canceled) {
                return false;
            }
            if (!layer.isRenamed()) {
                layer.setName(file.getName());
            }
            layer.setAssociatedFile(file);
            if (layer instanceof AbstractModifiableLayer) {
                ((AbstractModifiableLayer) layer).onPostSaveToFile();
            }
        } catch (IOException | InvalidPathException e) {
            showAndLogException(e);
            return false;
        }
        addToFileOpenHistory(file);
        Notification doneNotification = new Notification(tr("Successfully saved file {0}", file.getName())).setIcon(icon);
        GuiHelper.runInEDT(() -> doneNotification.replaceExisting(savingNotification));
        return true;
    }

    protected abstract File getFile(Layer layer);

    @Override
    protected boolean listenToSelectionChange() {
        return false;
    }

    @Override
    protected void updateEnabledState() {
        Layer activeLayer = getLayerManager().getActiveLayer();
        setEnabled(activeLayer != null && activeLayer.isSavable());
    }

    /**
     * Creates a new "Save" dialog for a single {@link ExtensionFileFilter} and makes it visible.<br>
     * When the user has chosen a file, checks the file extension, and confirms overwrite if needed.
     *
     * @param title The dialog title
     * @param filter The dialog file filter
     * @return The output {@code File}
     * @see DiskAccessAction#createAndOpenFileChooser(boolean, boolean, String, FileFilter, int, String)
     * @since 5456
     */
    public static File createAndOpenSaveFileChooser(String title, ExtensionFileFilter filter) {
        AbstractFileChooser fc = createAndOpenFileChooser(false, false, title, filter, JFileChooser.FILES_ONLY, null);
        return checkFileAndConfirmOverWrite(fc, filter.getDefaultExtension());
    }

    /**
     * Creates a new "Save" dialog for a given file extension and makes it visible.<br>
     * When the user has chosen a file, checks the file extension, and confirms overwrite if needed.
     *
     * @param title The dialog title
     * @param extension The file extension
     * @return The output {@code File}
     * @see DiskAccessAction#createAndOpenFileChooser(boolean, boolean, String, String)
     */
    public static File createAndOpenSaveFileChooser(String title, String extension) {
        AbstractFileChooser fc = createAndOpenFileChooser(false, false, title, extension);
        return checkFileAndConfirmOverWrite(fc, extension);
    }

    /**
     * Checks if selected filename has the given extension. If not, adds the extension and asks for overwrite if filename exists.
     *
     * @param fc FileChooser where file was already selected
     * @param extension file extension
     * @return the {@code File} or {@code null} if the user cancelled the dialog.
     */
    public static File checkFileAndConfirmOverWrite(AbstractFileChooser fc, String extension) {
        if (fc == null)
            return null;
        File file = fc.getSelectedFile();

        FileFilter ff = fc.getFileFilter();
        if (!ff.accept(file)) {
            // Extension of another filefilter given ?
            for (FileFilter cff : fc.getChoosableFileFilters()) {
                if (cff.accept(file)) {
                    fc.setFileFilter(cff);
                    return file;
                }
            }
            // No filefilter accepts current filename, add default extension
            String fn = file.getPath();
            if (extension != null && ff.accept(new File(fn + '.' + extension))) {
                fn += '.' + extension;
            } else if (ff instanceof ExtensionFileFilter) {
                fn += '.' + ((ExtensionFileFilter) ff).getDefaultExtension();
            }
            file = new File(fn);
            if (!fc.getSelectedFile().exists() && !confirmOverwrite(file))
                return null;
        }
        return file;
    }

    /**
     * Asks user to confirm overwiting a file.
     * @param file file to overwrite
     * @return {@code true} if the file can be written
     */
    public static boolean confirmOverwrite(File file) {
        if (file == null || file.exists()) {
            return new ExtendedDialog(
                    MainApplication.getMainFrame(),
                    tr("Overwrite"),
                    tr("Overwrite"), tr("Cancel"))
                .setContent(tr("File exists. Overwrite?"))
                .setButtonIcons("save", "cancel")
                .showDialog()
                .getValue() == 1;
        }
        return true;
    }

    static void addToFileOpenHistory(File file) {
        final String filepath;
        try {
            filepath = file.getCanonicalPath();
        } catch (IOException ign) {
            Logging.warn(ign);
            return;
        }

        int maxsize = Math.max(0, Config.getPref().getInt("file-open.history.max-size", 15));
        Collection<String> oldHistory = Config.getPref().getList("file-open.history");
        List<String> history = new LinkedList<>(oldHistory);
        history.remove(filepath);
        history.add(0, filepath);
        PreferencesUtils.putListBounded(Config.getPref(), "file-open.history", maxsize, history);
    }

    static void showAndLogException(Exception e) {
        GuiHelper.runInEDT(() ->
        JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                tr("<html>An error occurred while saving.<br>Error is:<br>{0}</html>",
                        Utils.escapeReservedCharactersHTML(e.getClass().getSimpleName() + " - " + e.getMessage())),
                tr("Error"),
                JOptionPane.ERROR_MESSAGE
                ));

        Logging.error(e);
    }
}
