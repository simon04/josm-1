// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.io;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;

import org.openstreetmap.josm.data.osm.Changeset;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.io.UploadTextComponentValidator.UploadAreaValidator;
import org.openstreetmap.josm.gui.io.UploadTextComponentValidator.UploadCommentValidator;
import org.openstreetmap.josm.gui.io.UploadTextComponentValidator.UploadSourceValidator;
import org.openstreetmap.josm.gui.widgets.HistoryComboBox;
import org.openstreetmap.josm.gui.widgets.JMultilineLabel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Utils;

/**
 * BasicUploadSettingsPanel allows to enter the basic parameters required for uploading data.
 * @since 2599
 */
public class BasicUploadSettingsPanel extends JPanel {
    /**
     * Preference name for history collection
     */
    public static final String HISTORY_KEY = "upload.comment.history";
    /**
     * Preference name for last used upload comment
     */
    public static final String HISTORY_LAST_USED_KEY = "upload.comment.last-used";
    /**
     * Preference name for the max age search comments may have
     */
    public static final String HISTORY_MAX_AGE_KEY = "upload.comment.max-age";
    /**
     * Preference name for the history of source values
     */
    public static final String SOURCE_HISTORY_KEY = "upload.source.history";

    /** the history combo box for the upload comment */
    private final HistoryComboBox hcbUploadComment = new HistoryComboBox();
    private final HistoryComboBox hcbUploadSource = new HistoryComboBox();
    private final transient JCheckBox obtainSourceAutomatically = new JCheckBox(
            tr("Automatically obtain source from current layers"));
    /** the panel with a summary of the upload parameters */
    private final UploadParameterSummaryPanel pnlUploadParameterSummary = new UploadParameterSummaryPanel();
    /** the checkbox to request feedback from other users */
    private final JCheckBox cbRequestReview = new JCheckBox(tr("I would like someone to review my edits."));
    private final JLabel areaValidatorFeedback = new JLabel();
    private final UploadAreaValidator areaValidator = new UploadAreaValidator(new JTextField(), areaValidatorFeedback);
    /** the changeset comment model */
    private final transient ChangesetCommentModel changesetCommentModel;
    private final transient ChangesetCommentModel changesetSourceModel;
    private final transient ChangesetReviewModel changesetReviewModel;
    private final transient JLabel uploadCommentFeedback = new JLabel();
    private final transient UploadCommentValidator uploadCommentValidator = new UploadCommentValidator(
            hcbUploadComment.getEditorComponent(), uploadCommentFeedback);
    private final transient JLabel hcbUploadSourceFeedback = new JLabel();
    private final transient UploadSourceValidator uploadSourceValidator = new UploadSourceValidator(
            hcbUploadSource.getEditorComponent(), hcbUploadSourceFeedback);

    protected JPanel buildUploadCommentPanel() {
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setBorder(BorderFactory.createTitledBorder(tr("Provide a brief comment for the changes you are uploading:")));

        hcbUploadComment.setToolTipText(tr("Enter an upload comment"));
        hcbUploadComment.setMaxTextLength(Changeset.MAX_CHANGESET_TAG_LENGTH);
        populateHistoryComboBox(hcbUploadComment, HISTORY_KEY, new LinkedList<>());
        CommentModelListener commentModelListener = new CommentModelListener(hcbUploadComment, changesetCommentModel);
        hcbUploadComment.getEditor().addActionListener(commentModelListener);
        hcbUploadComment.getEditorComponent().addFocusListener(commentModelListener);
        pnl.add(hcbUploadComment, GBC.eol().fill(GBC.HORIZONTAL));
        pnl.add(uploadCommentFeedback, GBC.eol().insets(0, 3, 0, 0).fill(GBC.HORIZONTAL));
        return pnl;
    }

    protected JPanel buildUploadSourcePanel() {
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setBorder(BorderFactory.createTitledBorder(tr("Specify the data source for the changes")));

        JEditorPane obtainSourceOnce = new JMultilineLabel(
                "<html>(<a href=\"urn:changeset-source\">" + tr("just once") + "</a>)</html>");
        obtainSourceOnce.addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
                automaticallyAddSource();
            }
        });
        obtainSourceAutomatically.setSelected(Config.getPref().getBoolean("upload.source.obtainautomatically", false));
        obtainSourceAutomatically.addActionListener(e -> {
            if (obtainSourceAutomatically.isSelected())
                automaticallyAddSource();

            obtainSourceOnce.setVisible(!obtainSourceAutomatically.isSelected());
        });
        JPanel obtainSource = new JPanel(new GridBagLayout());
        obtainSource.add(obtainSourceAutomatically, GBC.std().anchor(GBC.WEST));
        obtainSource.add(obtainSourceOnce, GBC.std().anchor(GBC.WEST));
        obtainSource.add(new JLabel(), GBC.eol().fill(GBC.HORIZONTAL));
        if (Config.getPref().getBoolean("upload.show.automatic.source", true)) {
            pnl.add(obtainSource, GBC.eol().insets(0, 0, 10, 3).fill(GBC.HORIZONTAL));
        }

        hcbUploadSource.setToolTipText(tr("Enter a source"));
        hcbUploadSource.setMaxTextLength(Changeset.MAX_CHANGESET_TAG_LENGTH);
        populateHistoryComboBox(hcbUploadSource, SOURCE_HISTORY_KEY, getDefaultSources());
        CommentModelListener sourceModelListener = new CommentModelListener(hcbUploadSource, changesetSourceModel);
        hcbUploadSource.getEditor().addActionListener(sourceModelListener);
        hcbUploadSource.getEditorComponent().addFocusListener(sourceModelListener);
        pnl.add(hcbUploadSource, GBC.eol().fill(GBC.HORIZONTAL));
        pnl.add(hcbUploadSourceFeedback, GBC.eol().insets(0, 3, 0, 0).fill(GBC.HORIZONTAL));
        if (obtainSourceAutomatically.isSelected()) {
            automaticallyAddSource();
        }
        pnl.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                if (obtainSourceAutomatically.isSelected())
                    automaticallyAddSource();
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
                // Do nothing
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
                // Do nothing
            }
        });
        return pnl;
    }

    /**
     * Add the source tags
     */
    protected void automaticallyAddSource() {
        final String source = MainApplication.getMap().mapView.getLayerInformationForSourceTag();
        hcbUploadSource.getModel().setSelectedItem(null); // fix #20134
        hcbUploadSource.setText(Utils.shortenString(source, Changeset.MAX_CHANGESET_TAG_LENGTH));
        changesetSourceModel.setComment(hcbUploadSource.getText()); // Fix #9965
    }

    /**
     * Refreshes contents of upload history combo boxes from preferences.
     */
    protected void refreshHistoryComboBoxes() {
        populateHistoryComboBox(hcbUploadComment, HISTORY_KEY, new LinkedList<>());
        populateHistoryComboBox(hcbUploadSource, SOURCE_HISTORY_KEY, getDefaultSources());
    }

    private static void populateHistoryComboBox(HistoryComboBox hcb, String historyKey, List<String> defaultValues) {
        hcb.setPossibleItemsTopDown(Config.getPref().getList(historyKey, defaultValues));
        hcb.discardAllUndoableEdits();
    }

    /**
     * Discards undoable edits of upload history combo boxes.
     */
    protected void discardAllUndoableEdits() {
        hcbUploadComment.discardAllUndoableEdits();
        hcbUploadSource.discardAllUndoableEdits();
    }

    /**
     * Returns the default list of sources.
     * @return the default list of sources
     */
    public static List<String> getDefaultSources() {
        return Arrays.asList("knowledge", "survey", "Bing");
    }

    /**
     * Returns the list of {@link UploadTextComponentValidator} defined by this panel.
     * @return the list of {@code UploadTextComponentValidator} defined by this panel.
     * @since 17238
     */
    protected List<UploadTextComponentValidator> getUploadTextValidators() {
        return Arrays.asList(areaValidator, uploadCommentValidator, uploadSourceValidator);
    }

    protected void build() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        add(buildUploadCommentPanel());
        add(buildUploadSourcePanel());
        add(pnlUploadParameterSummary);
        if (Config.getPref().getBoolean("upload.show.review.request", true)) {
            JPanel pnl = new JPanel(new GridBagLayout());
            pnl.add(cbRequestReview, GBC.eol().fill(GBC.HORIZONTAL));
            add(pnl);
            cbRequestReview.addItemListener(e -> changesetReviewModel.setReviewRequested(e.getStateChange() == ItemEvent.SELECTED));
        }
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.add(areaValidatorFeedback, GBC.eol().fill(GBC.HORIZONTAL));
        add(pnl);
    }

    /**
     * Creates the panel
     *
     * @param changesetCommentModel the model for the changeset comment. Must not be null
     * @param changesetSourceModel the model for the changeset source. Must not be null.
     * @param changesetReviewModel the model for the changeset review. Must not be null.
     * @throws NullPointerException if a model is null
     * @since 12719 (signature)
     */
    public BasicUploadSettingsPanel(ChangesetCommentModel changesetCommentModel, ChangesetCommentModel changesetSourceModel,
            ChangesetReviewModel changesetReviewModel) {
        this.changesetCommentModel = Objects.requireNonNull(changesetCommentModel, "changesetCommentModel");
        this.changesetSourceModel = Objects.requireNonNull(changesetSourceModel, "changesetSourceModel");
        this.changesetReviewModel = Objects.requireNonNull(changesetReviewModel, "changesetReviewModel");
        changesetCommentModel.addChangeListener(new ChangesetCommentChangeListener(hcbUploadComment));
        changesetSourceModel.addChangeListener(new ChangesetCommentChangeListener(hcbUploadSource));
        changesetReviewModel.addChangeListener(new ChangesetReviewChangeListener());
        build();
    }

    void setUploadTagDownFocusTraversalHandlers(final ActionListener handler) {
        setHistoryComboBoxDownFocusTraversalHandler(handler, hcbUploadComment);
        setHistoryComboBoxDownFocusTraversalHandler(handler, hcbUploadSource);
    }

    private static void setHistoryComboBoxDownFocusTraversalHandler(ActionListener handler, HistoryComboBox hcb) {
        hcb.getEditor().addActionListener(handler);
        hcb.getEditorComponent().addKeyListener(new HistoryComboBoxKeyAdapter(hcb, handler));
    }

    /**
     * Remembers the user input in the preference settings
     */
    public void rememberUserInput() {
        // store the history of comments
        if (getHistoryMaxAgeKey() > 0) {
            hcbUploadComment.addCurrentItemToHistory();
            Config.getPref().putList(HISTORY_KEY, hcbUploadComment.getHistory());
            Config.getPref().putLong(HISTORY_LAST_USED_KEY, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        }
        // store the history of sources
        hcbUploadSource.addCurrentItemToHistory();
        Config.getPref().putList(SOURCE_HISTORY_KEY, hcbUploadSource.getHistory());

        // store current value of obtaining source automatically
        Config.getPref().putBoolean("upload.source.obtainautomatically", obtainSourceAutomatically.isSelected());
    }

    /**
     * Initializes the panel for user input
     */
    public void startUserInput() {
        hcbUploadComment.requestFocusInWindow();
        hcbUploadComment.getEditorComponent().requestFocusInWindow();
        uploadCommentValidator.validate();
        uploadSourceValidator.validate();
    }

    /**
     * Initializes editing of upload comment.
     */
    public void initEditingOfUploadComment() {
        hcbUploadComment.getEditor().selectAll();
        hcbUploadComment.requestFocusInWindow();
    }

    /**
     * Initializes editing of upload source.
     */
    public void initEditingOfUploadSource() {
        hcbUploadSource.getEditor().selectAll();
        hcbUploadSource.requestFocusInWindow();
    }

    void setUploadedPrimitives(List<OsmPrimitive> primitives) {
        areaValidator.computeArea(primitives);
    }

    /**
     * Returns the panel that displays a summary of data the user is about to upload.
     * @return the upload parameter summary panel
     */
    public UploadParameterSummaryPanel getUploadParameterSummaryPanel() {
        return pnlUploadParameterSummary;
    }

    /**
     * Forces update of comment/source model if matching text field is focused.
     * @since 14977
     */
    public void forceUpdateActiveField() {
        updateModelIfFocused(hcbUploadComment, changesetCommentModel);
        updateModelIfFocused(hcbUploadSource, changesetSourceModel);
    }

    private static void updateModelIfFocused(HistoryComboBox hcb, ChangesetCommentModel changesetModel) {
        if (hcb.getEditorComponent().hasFocus()) {
            changesetModel.setComment(hcb.getText());
        }
    }

    static long getHistoryMaxAgeKey() {
        return Config.getPref().getLong(HISTORY_MAX_AGE_KEY, TimeUnit.HOURS.toSeconds(4));
    }

    static long getHistoryLastUsedKey() {
        return Config.getPref().getLong(BasicUploadSettingsPanel.HISTORY_LAST_USED_KEY, 0);
    }

    static final class HistoryComboBoxKeyAdapter extends KeyAdapter {
        private final HistoryComboBox hcb;
        private final ActionListener handler;

        HistoryComboBoxKeyAdapter(HistoryComboBox hcb, ActionListener handler) {
            this.hcb = hcb;
            this.handler = handler;
        }

        @Override
        public void keyTyped(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_TAB) {
                handler.actionPerformed(new ActionEvent(hcb, 0, "focusDown"));
            }
        }
    }

    /**
     * Updates the changeset comment model upon changes in the input field.
     */
    static class CommentModelListener extends FocusAdapter implements ActionListener {

        private final HistoryComboBox source;
        private final ChangesetCommentModel destination;

        CommentModelListener(HistoryComboBox source, ChangesetCommentModel destination) {
            this.source = source;
            this.destination = destination;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            destination.setComment(source.getText());
        }

        @Override
        public void focusLost(FocusEvent e) {
            destination.setComment(source.getText());
        }
    }

    /**
     * Observes the changeset comment model and keeps the comment input field
     * in sync with the current changeset comment
     */
    static class ChangesetCommentChangeListener implements ChangeListener {

        private final HistoryComboBox destination;

        ChangesetCommentChangeListener(HistoryComboBox destination) {
            this.destination = destination;
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            if (!(e.getSource() instanceof ChangesetCommentModel)) return;
            String newComment = ((ChangesetCommentModel) e.getSource()).getComment();
            if (!destination.getText().equals(newComment)) {
                destination.setText(newComment);
            }
        }
    }

    /**
     * Observes the changeset review model and keeps the review checkbox
     * in sync with the current changeset review request
     */
    class ChangesetReviewChangeListener implements ChangeListener {
        @Override
        public void stateChanged(ChangeEvent e) {
            if (!(e.getSource() instanceof ChangesetReviewModel)) return;
            boolean newState = ((ChangesetReviewModel) e.getSource()).isReviewRequested();
            if (cbRequestReview.isSelected() != newState) {
                cbRequestReview.setSelected(newState);
            }
        }
    }
}
