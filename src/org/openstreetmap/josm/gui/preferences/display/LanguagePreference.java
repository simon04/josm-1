// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.display;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.LanguageInfo;

/**
 * Language preferences.
 * @since 1065
 */
public class LanguagePreference extends DefaultTabPreferenceSetting {

    private static final String LANGUAGE = "language";

    /**
     * Factory used to create a new {@code LanguagePreference}.
     */
    public static class Factory implements PreferenceSettingFactory {
        @Override
        public PreferenceSetting createPreferenceSetting() {
            return new LanguagePreference();
        }
    }

    LanguagePreference() {
        super(/* ICON(preferences/) */ "language", tr("Language"), tr("Change the language of JOSM."));
    }

    /** the combo box with the available locales */
    private JosmComboBox<Locale> langCombo;

    @Override
    public void addGui(final PreferenceTabbedPane gui) {
        LanguageComboBoxModel model = new LanguageComboBoxModel();
        // Selecting the language BEFORE the JComboBox listens to model changes speed up initialization by ~35ms (see #7386)
        // See https://stackoverflow.com/questions/3194958/fast-replacement-for-jcombobox-basiccomboboxui
        model.selectLanguage(Config.getPref().get(LANGUAGE));
        langCombo = new JosmComboBox<>(model);
        langCombo.setRenderer(new LanguageCellRenderer());

        final JPanel panel = new JPanel(new GridBagLayout());
        panel.add(new JLabel(tr("Language")), GBC.std().insets(20, 0, 0, 0));
        panel.add(GBC.glue(5, 0), GBC.std().fill(GBC.HORIZONTAL));
        panel.add(langCombo, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(Box.createVerticalGlue(), GBC.eol().fill(GBC.BOTH));

        createPreferenceTabWithScrollPane(gui, panel);
    }

    @Override
    public boolean ok() {
        if (langCombo.getSelectedItem() == null)
            return Config.getPref().put(LANGUAGE, null);
        else
            return Config.getPref().put(LANGUAGE,
                    LanguageInfo.getJOSMLocaleCode((Locale) langCombo.getSelectedItem()));
    }

    private static class LanguageComboBoxModel extends DefaultComboBoxModel<Locale> {
        private final List<Locale> data = new ArrayList<>();

        LanguageComboBoxModel() {
            data.add(0, null);
            I18n.getAvailableTranslations()
                    .sorted(Comparator.comparing(Locale::getDisplayLanguage))
                    .forEachOrdered(data::add);
        }

        private void selectLanguage(String language) {
            setSelectedItem(null);
            if (language != null) {
                String lang = LanguageInfo.getJavaLocaleCode(language);
                data.stream()
                        .filter(locale -> locale != null && locale.toString().equals(lang))
                        .findFirst()
                        .ifPresent(this::setSelectedItem);
            }
        }

        @Override
        public Locale getElementAt(int index) {
            return data.get(index);
        }

        @Override
        public int getSize() {
            return data.size();
        }
    }

    private static class LanguageCellRenderer implements ListCellRenderer<Locale> {
        private final DefaultListCellRenderer dispatch;

        /**
         * Constructs a new {@code LanguageCellRenderer}.
         */
        LanguageCellRenderer() {
            this.dispatch = new DefaultListCellRenderer();
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Locale> list, Locale l,
                int index, boolean isSelected, boolean cellHasFocus) {
            return dispatch.getListCellRendererComponent(list,
                    l == null
                            ? tr("Default (Auto determined)")
                            : LanguageInfo.getDisplayName(l),
                    index, isSelected, cellHasFocus);
        }
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    @Override
    public String getHelpContext() {
        return HelpUtil.ht("/Preferences/LanguagePreference");
    }
}
