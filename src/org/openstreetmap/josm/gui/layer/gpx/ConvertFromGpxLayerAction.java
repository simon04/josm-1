// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.gpx;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.openstreetmap.josm.data.gpx.GpxConstants;
import org.openstreetmap.josm.data.gpx.GpxExtension;
import org.openstreetmap.josm.data.gpx.GpxExtensionCollection;
import org.openstreetmap.josm.data.gpx.IGpxTrack;
import org.openstreetmap.josm.data.gpx.IGpxTrackSegment;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.date.DateUtils;

/**
 * Converts a {@link GpxLayer} to a {@link OsmDataLayer}.
 * @since 14129 (extracted from {@link ConvertToDataLayerAction})
 */
public class ConvertFromGpxLayerAction extends ConvertToDataLayerAction<GpxLayer> {

    private static final String GPX_SETTING = "gpx.convert-tags";

    /**
     * Creates a new {@code FromGpxLayer}.
     * @param layer the source layer
     */
    public ConvertFromGpxLayerAction(GpxLayer layer) {
        super(layer);
    }

    @Override
    public DataSet convert() {
        final DataSet ds = new DataSet();
        ds.setGPXNamespaces(layer.data.getNamespaces());

        List<String> keys = new ArrayList<>(); // note that items in this list don't have the GPX_PREFIX
        String convertTags = Config.getPref().get(GPX_SETTING, "ask");
        boolean check = "list".equals(convertTags) || "ask".equals(convertTags);
        boolean none = "no".equals(convertTags); // no need to convert tags when no dialog will be shown anyways

        for (IGpxTrack trk : layer.data.getTracks()) {
            for (IGpxTrackSegment segment : trk.getSegments()) {
                List<Node> nodes = new ArrayList<>();
                for (WayPoint p : segment.getWayPoints()) {
                    Node n = new Node(p.getCoor());
                    addAttributes(p.getAttributes(), n, keys, check, none);
                    if (!none) {
                        addExtensions(p.getExtensions(), n, false, keys, check);
                    }
                    ds.addPrimitive(n);
                    nodes.add(n);
                }
                Way w = new Way();
                w.setNodes(nodes);
                addAttributes(trk.getAttributes(), w, keys, check, none);
                addAttributes(segment.getAttributes(), w, keys, check, none);
                if (!none) {
                    addExtensions(trk.getExtensions(), w, false, keys, check);
                    addExtensions(segment.getExtensions(), w, true, keys, check);
                }
                ds.addPrimitive(w);
            }
        }
        //gpx.convert-tags: all, list, *ask, no
        //gpx.convert-tags.last: *all, list, no
        //gpx.convert-tags.list.yes
        //gpx.convert-tags.list.no
        List<String> listPos = Config.getPref().getList(GPX_SETTING + ".list.yes");
        List<String> listNeg = Config.getPref().getList(GPX_SETTING + ".list.no");
        if (check && !keys.isEmpty()) {
            // Either "list" or "ask" was stored in the settings, so the Nodes have to be filtered after all tags have been processed
            List<String> allTags = new ArrayList<>(listPos);
            allTags.addAll(listNeg);
            if (!allTags.containsAll(keys) || "ask".equals(convertTags)) {
                // not all keys are in positive/negative list, so we have to ask the user
                TagConversionDialogResponse res = showTagConversionDialog(keys, listPos, listNeg);
                if (res.sel == null) {
                    return null;
                }
                listPos = res.listPos;

                if ("no".equals(res.sel)) {
                    // User just chose not to convert any tags, but that was unknown before the initial conversion
                    return filterDataSet(ds, null);
                } else if ("all".equals(res.sel)) {
                    return ds;
                }
            }
            if (!listPos.containsAll(keys)) {
                return filterDataSet(ds, listPos);
            }
        }
        return ds;
    }

    private static void addAttributes(Map<String, Object> attr, OsmPrimitive p, List<String> keys, boolean check, boolean none) {
        for (Entry<String, Object> entry : attr.entrySet()) {
            String key = entry.getKey();
            Object obj = entry.getValue();
            if (check && !keys.contains(key) && (obj instanceof String || obj instanceof Number || obj instanceof Date)) {
                keys.add(key);
            }
            if (!none && (obj instanceof String || obj instanceof Number)) {
                // only convert when required
                p.put(GpxConstants.GPX_PREFIX + key, obj.toString());
            } else if (obj instanceof Date && GpxConstants.PT_TIME.equals(key)) {
                // timestamps should always be converted
                Date date = (Date) obj;
                if (!none) { //... but the tag will only be set when required
                    p.put(GpxConstants.GPX_PREFIX + key, DateUtils.fromDate(date));
                }
                p.setTimestamp(date);
            }
        }
    }

    private static void addExtensions(GpxExtensionCollection exts, OsmPrimitive p, boolean seg, List<String> keys, boolean check) {
        for (GpxExtension ext : exts) {
            String value = ext.getValue();
            if (value != null && !value.isEmpty()) {
                String extpre = "extension:";
                String pre = ext.getPrefix();
                if (pre == null || pre.isEmpty()) {
                    pre = "other";
                }
                // needs to be distinguished since both track and segment extensions are applied to the resulting way
                String segpre = seg ? "segment:" : "";
                String key = ext.getFlatKey();
                String fullkey = GpxConstants.GPX_PREFIX + extpre + pre + ":" + segpre + key;
                if (GpxConstants.EXTENSION_ABBREVIATIONS.containsKey(fullkey)) {
                    fullkey = GpxConstants.EXTENSION_ABBREVIATIONS.get(fullkey);
                }
                if (check && !keys.contains(fullkey)) {
                    keys.add(fullkey);
                }
                p.put(fullkey, value);
            }
            addExtensions(ext.getExtensions(), p, seg, keys, check);
        }
    }

    /**
     * Filters the tags of the given {@link DataSet}
     * @param ds The {@link DataSet}
     * @param listPos A {@code List<String>} containing the tags (without prefix) to be kept, can be {@code null} if all tags are to be removed
     * @return The {@link DataSet}
     * @since 14103
     */
    public DataSet filterDataSet(DataSet ds, List<String> listPos) {
        for (OsmPrimitive p : ds.getPrimitives(p -> p instanceof Node || p instanceof Way)) {
            p.visitKeys((primitive, key, value) -> {
                String listkey;
                if (listPos != null && key.startsWith(GpxConstants.GPX_PREFIX)) {
                    listkey = key.substring(GpxConstants.GPX_PREFIX.length());
                } else {
                    listkey = key;
                }
                if (listPos == null || !listPos.contains(listkey)) {
                    p.put(key, null);
                }
            });
        }
        return ds;
    }

    /**
     * Shows the TagConversionDialog asking the user whether to keep all, some or no tags
     * @param keys The keys present during the current conversion
     * @param listPos The keys that were previously selected
     * @param listNeg The keys that were previously unselected
     * @return {@link TagConversionDialogResponse} containing the selection
     */
    private static TagConversionDialogResponse showTagConversionDialog(List<String> keys, List<String> listPos, List<String> listNeg) {
        TagConversionDialogResponse res = new TagConversionDialogResponse(listPos, listNeg);
        String lSel = Config.getPref().get(GPX_SETTING + ".last", "all");

        JPanel p = new JPanel(new GridBagLayout());
        ButtonGroup r = new ButtonGroup();

        p.add(new JLabel(
                tr("The GPX layer contains fields that can be converted to OSM tags. How would you like to proceed?")),
                GBC.eol());
        JRadioButton rAll = new JRadioButton(tr("Convert all fields"), "all".equals(lSel));
        r.add(rAll);
        p.add(rAll, GBC.eol());

        JRadioButton rList = new JRadioButton(tr("Only convert the following fields:"), "list".equals(lSel));
        r.add(rList);
        p.add(rList, GBC.eol());

        JPanel q = new JPanel();

        List<JCheckBox> checkList = new ArrayList<>();
        for (String key : keys) {
            JCheckBox cTmp = new JCheckBox(key, !listNeg.contains(key));
            checkList.add(cTmp);
            q.add(cTmp);
        }

        q.setBorder(BorderFactory.createEmptyBorder(0, 20, 5, 0));
        p.add(q, GBC.eol());

        JRadioButton rNone = new JRadioButton(tr("Do not convert any fields"), "no".equals(lSel));
        r.add(rNone);
        p.add(rNone, GBC.eol());

        ActionListener enabler = new TagConversionDialogRadioButtonActionListener(checkList, true);
        ActionListener disabler = new TagConversionDialogRadioButtonActionListener(checkList, false);

        if (!"list".equals(lSel)) {
            disabler.actionPerformed(null);
        }

        rAll.addActionListener(disabler);
        rList.addActionListener(enabler);
        rNone.addActionListener(disabler);

        ExtendedDialog ed = new ExtendedDialog(MainApplication.getMainFrame(), tr("Options"),
                tr("Convert"), tr("Convert and remember selection"), tr("Cancel"))
                .setButtonIcons("exportgpx", "exportgpx", "cancel").setContent(p);
        int ret = ed.showDialog().getValue();

        if (ret == 1 || ret == 2) {
            for (JCheckBox cItem : checkList) {
                String key = cItem.getText();
                if (cItem.isSelected()) {
                    if (!res.listPos.contains(key)) {
                        res.listPos.add(key);
                    }
                    res.listNeg.remove(key);
                } else {
                    if (!res.listNeg.contains(key)) {
                        res.listNeg.add(key);
                    }
                    res.listPos.remove(key);
                }
            }
            if (rAll.isSelected()) {
                res.sel = "all";
            } else if (rNone.isSelected()) {
                res.sel = "no";
            }
            Config.getPref().put(GPX_SETTING + ".last", res.sel);
            if (ret == 2) {
                Config.getPref().put(GPX_SETTING, res.sel);
            } else {
                Config.getPref().put(GPX_SETTING, "ask");
            }
            Config.getPref().putList(GPX_SETTING + ".list.yes", res.listPos);
            Config.getPref().putList(GPX_SETTING + ".list.no", res.listNeg);
        } else {
            res.sel = null;
        }
        return res;
    }

    private static class TagConversionDialogResponse {

        final List<String> listPos;
        final List<String> listNeg;
        String sel = "list";

        TagConversionDialogResponse(List<String> p, List<String> n) {
            listPos = new ArrayList<>(p);
            listNeg = new ArrayList<>(n);
        }
    }

    private static class TagConversionDialogRadioButtonActionListener implements ActionListener {

        private final boolean enable;
        private final List<JCheckBox> checkList;

        TagConversionDialogRadioButtonActionListener(List<JCheckBox> chks, boolean en) {
            enable = en;
            checkList = chks;
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            for (JCheckBox ch : checkList) {
                ch.setEnabled(enable);
            }
        }
    }
}
