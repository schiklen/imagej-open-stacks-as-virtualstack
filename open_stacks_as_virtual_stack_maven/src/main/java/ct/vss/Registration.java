package ct.vss;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;
import mpicbg.imglib.algorithm.fft.PhaseCorrelation;
import mpicbg.imglib.image.ImagePlusAdapter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static ij.IJ.log;


/**
 * Created by tischi on 31/10/16.
 */

// todo: add a clickable github link at the bottom of the plugin

// todo: how to behave when the track is leaving the image bounds?

public class Registration implements PlugIn, ImageListener {

    VirtualStackOfStacks vss;
    ImagePlus imp;
    private static NonBlockingGenericDialog gd;
    private final static Point3D pOnes = new Point3D(1,1,1);
    // gui variables
    int gui_ntTracking, gui_bg;
    int gui_iterations = 6;
    Point3D gui_pCenterOfMassRadii;
    Point3D gui_pCropRadii;
    Point3D gui_pSubSample = new Point3D(1,1,1);
    int gui_tSubSample = 1;
    ArrayList<Track> Tracks = new ArrayList<Track>();
    ArrayList<Roi> rTrackStarts = new ArrayList<Roi>();
    String gui_trackingMethod = "center of mass";
    String gui_centeringMethod = "center of mass";
    TrackTable trackTable;
    long trackStatsStartTime;
    long trackStatsReportDelay = 200;
    long trackStatsLastReport = System.currentTimeMillis();
    int totalTimePointsToBeTracked = 0;
    AtomicInteger totalTimePointsTracked = new AtomicInteger(0);
    AtomicInteger totalTimeSpentTracking = new AtomicInteger(0);
    long trackStatsLastTrackStarted;
    int trackStatsTotalPointsTrackedAtLastStart;
    TrackingGUI trackingGUI;

    // todo: remove the background variable?!

    // todo: put actual tracking into different class

    public Registration() {
    }

    public Registration(ImagePlus imp) {
        this.imp = imp;
        initialize();
    }

    // to-do: add labels in front of the buttons to make the text smaller
    class TrackingGUI implements ActionListener, FocusListener {

        JFrame frame;

        String[] texts = {
                "Object tracking radii: x, y, z [pix]",
                "Object cropping radii: x, y, z [pix]",
                "Tracking sub-sampling: dx, dy, dz, dt [pix]",
                "Track length [frames]"
        };

        String[] actions = {
                //"Add Track Start",
                "Track selected object",
                "Show tracked objects",
                "Track whole data set",
                "Show tracked data set",
                "Show track table",
                "Save track table",
                "Clear track table",
                "Report issue"
        };


        String[] defaults = {
                String.valueOf((int) gui_pCenterOfMassRadii.getX()) + "," + (int) gui_pCenterOfMassRadii.getY() + "," +String.valueOf((int) gui_pCenterOfMassRadii.getZ()),
                String.valueOf((int) gui_pCropRadii.getX()) + "," + ((int) gui_pCropRadii.getY()) + "," + String.valueOf((int) gui_pCropRadii.getZ()),
                String.valueOf((int) gui_pSubSample.getX() + "," + (int) gui_pSubSample.getY() + "," + (int) gui_pSubSample.getZ() + "," + gui_tSubSample),
                String.valueOf(imp.getNFrames())
        };

        String[] comboNames = {
                "Tracking method"
        };

        String[][] combos = {
                {"center of mass","correlation"}
        };


        ExecutorService es = Executors.newCachedThreadPool();

        public void TrackingGUI() {
        }

        public void showDialog() {

            frame = new JFrame("Big Data Tracker");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            Container c = frame.getContentPane();
            c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
            ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);

            String[] toolTipTexts = getToolTipFile("TrackAndCropHelp.html");

            //
            // TextFields
            //
            JTextField[] textFields = new JTextField[texts.length];
            JLabel[] labels = new JLabel[texts.length];

            int iToolTipText = 0;

            for (int i = 0; i < textFields.length; i++, iToolTipText++) {
                textFields[i] = new JTextField(6);
                textFields[i].setActionCommand(texts[i]);
                textFields[i].addActionListener(this);
                textFields[i].addFocusListener(this);
                textFields[i].setText(defaults[i]);
                textFields[i].setToolTipText(toolTipTexts[iToolTipText]);
                labels[i] = new JLabel(texts[i] + ": ");
                labels[i].setLabelFor(textFields[i]);
            }

            //
            // Buttons
            //
            JButton[] buttons = new JButton[actions.length];

            for (int i = 0; i < buttons.length; i++, iToolTipText++) {
                buttons[i] = new JButton(actions[i]);
                buttons[i].setActionCommand(actions[i]);
                buttons[i].addActionListener(this);
                buttons[i].setToolTipText(toolTipTexts[iToolTipText]);
            }

            //
            // ComboBoxes
            //

            JComboBox[] comboBoxes = new JComboBox[comboNames.length];
            JLabel[] comboLabels = new JLabel[comboNames.length];

            for (int i = 0; i < combos.length; i++, iToolTipText++) {
                comboBoxes[i] = new JComboBox(combos[i]);
                comboBoxes[i].setActionCommand(comboNames[i]);
                comboBoxes[i].addActionListener(this);
                comboBoxes[i].setToolTipText(toolTipTexts[iToolTipText]);
                comboLabels[i] = new JLabel(comboNames[i] + ": ");
                comboLabels[i].setLabelFor(comboBoxes[i]);
            }

            //
            // Panels
            //

            int i = 0, j = 0;
            ArrayList<JPanel> panels = new ArrayList<JPanel>();
            int iPanel = 0;

            for (int k = 0; k < textFields.length; k++) {
                panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
                panels.get(iPanel).add(labels[k]);
                panels.get(iPanel).add(textFields[k]);
                c.add(panels.get(iPanel++));
            }

            for (int k = 0; k < comboNames.length; k++) {
                panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
                panels.get(iPanel).add(comboLabels[k]);
                panels.get(iPanel).add(comboBoxes[k]);
                c.add(panels.get(iPanel++));
            }

            panels.add(new JPanel());
            panels.get(iPanel).add(buttons[i++]);
            panels.get(iPanel).add(buttons[i++]);
            c.add(panels.get(iPanel++));

            panels.add(new JPanel());
            panels.get(iPanel).add(buttons[i++]);
            panels.get(iPanel).add(buttons[i++]);
            c.add(panels.get(iPanel++));

            panels.add(new JPanel());
            panels.get(iPanel).add(buttons[i++]);
            panels.get(iPanel).add(buttons[i++]);
            panels.get(iPanel).add(buttons[i++]);
            c.add(panels.get(iPanel++));

            panels.add(new JPanel());
            panels.get(iPanel).add(buttons[i++]);
            c.add(panels.get(iPanel++));

            frame.pack();
            frame.setLocation(imp.getWindow().getX() + imp.getWindow().getWidth(), imp.getWindow().getY());
            frame.setVisible(true);

        }

        public void focusGained(FocusEvent e) {
            //
        }

        public void focusLost(FocusEvent e) {
            JTextField tf = (JTextField) e.getSource();
            if (!(tf == null)) {
                tf.postActionEvent();
            }
        }

        public void actionPerformed(ActionEvent e) {
            int i = 0, j = 0, k = 0;
            JFileChooser fc;
            final OpenStacksAsVirtualStack osv = new OpenStacksAsVirtualStack();

            if (e.getActionCommand().equals(actions[i++])) {

                //
                // Track selected object
                //

                if(gui_trackingMethod.equals("correlation")) {
                    IJ.showMessage("Object tracking with correlation is not yet implemented.");
                    return;
                }

                Roi r = imp.getRoi();
                if (r == null || !r.getTypeAsString().equals("Point")) {
                    IJ.showMessage("Please use IJ's 'Point selection tool' on image: '" + imp.getTitle()+"'");
                    return;
                }

                // Add track start ...
                int iNewTrack = addTrackStart(imp);

                // ... and start tracking immediately
                if( iNewTrack >= 0 ) {
                    trackStatsLastTrackStarted = System.currentTimeMillis();
                    trackStatsTotalPointsTrackedAtLastStart = totalTimePointsTracked.get();
                    es.execute(new Registration.TrackROI(iNewTrack, gui_tSubSample, gui_pSubSample, gui_pCenterOfMassRadii, gui_bg, gui_iterations));
                }

            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // View Object Tracks
                //

                showCroppedTracks();

            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // Track whole data set
                //

                // Add track start ...
                int iNewTrack = addTrackStartWholeDataSet(imp);

                // ... and start tracking immediately
                if( iNewTrack >= 0 ) {

                    trackStatsLastTrackStarted = System.currentTimeMillis();
                    trackStatsTotalPointsTrackedAtLastStart = totalTimePointsTracked.get();
                    es.execute(new Registration.TrackAll(iNewTrack, gui_pSubSample, gui_tSubSample, gui_bg));

                }

            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // View whole tracked Data set
                //

                showWholeDataSetAlongTracks();

            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // Show Table
                //

                showTrackTable();

            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // Save Table
                //

                TableModel model = trackTable.getTable().getModel();
                if(model == null) {
                    IJ.showMessage("There are no tracks yet.");
                    return;
                }
                fc = new JFileChooser(vss.getDirectory());
                if (fc.showSaveDialog(this.frame) == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    saveTrackTable(file);
                }

            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // Clear Table and all tracks
                //

                trackTable.clear();
                Tracks = new ArrayList<Track>();
                rTrackStarts = new ArrayList<Roi>();

            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // Report issue
                //

                if (Desktop.isDesktopSupported()) {
                    try {
                        final URI uri = new URI("https://github.com/tischi/imagej-open-stacks-as-virtualstack/issues");
                        Desktop.getDesktop().browse(uri);
                    } catch (URISyntaxException uriEx) {
                        IJ.showMessage(uriEx.toString());
                    } catch (IOException ioEx) {
                        IJ.showMessage(ioEx.toString());
                    }
                } else { /* TODO: error handling */ }

            }  else if (e.getActionCommand().equals(texts[k++])) {

                //
                // Tracking radii
                //

                JTextField source = (JTextField) e.getSource();
                String[] sA = source.getText().split(",");
                gui_pCenterOfMassRadii = new Point3D(new Integer(sA[0]), new Integer(sA[1]), new Integer(sA[2]));

            } else if (e.getActionCommand().equals(texts[k++])) {

                //
                // Cropping radii
                //

                JTextField source = (JTextField) e.getSource();
                String[] sA = source.getText().split(",");
                gui_pCropRadii = new Point3D(new Integer(sA[0]), new Integer(sA[1]), new Integer(sA[2]));

            } else if (e.getActionCommand().equals(texts[k++])) {

                //
                // Tracking sub-sampling
                //

                JTextField source = (JTextField) e.getSource();
                String[] sA = source.getText().split(",");
                gui_pSubSample = new Point3D(new Integer(sA[0]), new Integer(sA[1]), new Integer(sA[2]));
                gui_tSubSample = new Integer(sA[3]);

            } else if (e.getActionCommand().equals(texts[k++])) {

                //
                // Track length
                //
                JTextField source = (JTextField) e.getSource();
                gui_ntTracking = new Integer(source.getText());

            } else if (e.getActionCommand().equals(comboNames[j++])) {

                //
                // Tracking method
                //

                JComboBox cb = (JComboBox)e.getSource();
                gui_trackingMethod = (String)cb.getSelectedItem();

            }
        }

        public JFrame getFrame() {
            return frame;
        }

        private String[] getToolTipFile(String fileName) {
            ArrayList<String> toolTipTexts = new ArrayList<String>();

            //Get file from resources folder
            InputStream in = getClass().getResourceAsStream("/"+fileName);
            BufferedReader input = new BufferedReader(new InputStreamReader(in));
            Scanner scanner = new Scanner(input);
            StringBuilder sb = new StringBuilder("");

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if(line.equals("###")) {
                    toolTipTexts.add(sb.toString());
                    sb = new StringBuilder("");
                } else {
                    sb.append(line);
                }

            }

            scanner.close();


            return(toolTipTexts.toArray(new String[0]));
        }

    }

    private JFileChooser myJFileChooser = new JFileChooser(new File("."));

    public void run(String arg) {
        this.imp = IJ.getImage();
        initialize();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                showDialog();
            }
        });
    }

    public void showDialog(){
       trackingGUI = new TrackingGUI();
       trackingGUI.showDialog();
    }

    private void initialize() {

        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if(vss==null) {
            throw new IllegalArgumentException("Registration only works with VirtualStackOfStacks");
        }
        FileInfoSer[][][] infos = vss.getFileInfosSer();
        if(infos[0][0][0].compression==6) {
            IJ.showMessage(
                    "This is a ZIP compressed data set." +
                            "This will probably NOT WORK PROPERLY with this plugin!"
            );
        }
        this.vss = vss;
        gui_pCenterOfMassRadii = new Point3D(30,30,5);
        gui_pCropRadii = new Point3D(60,60,10);
        gui_ntTracking = imp.getNFrames();
        gui_bg = (int) imp.getProcessor().getMin();
        trackTable = new TrackTable();
        ImagePlus.addImageListener(this);
    }

    public void logStatus() {
        log("# Current track status:");
        if(Tracks.size()==0) {
            log("no tracks yet");
            return;
        }
        for(int iTrack=0; iTrack<Tracks.size(); iTrack++) {
            if(!Tracks.get(iTrack).completed) {
                log("track "+iTrack+": not completed");
            } else {
                log("track "+iTrack+": completed; length="+Tracks.get(iTrack).getLength());
            }
        }
    }

    class TrackTable  {
        JTable table;

        public TrackTable() {
            String[] columnNames = {"ID_T",
                    "X",
                    "Y",
                    "Z",
                    "T",
                    "ID"
                    //,
                    //"t_TotalSum",
                    //"t_ReadThis",
                    //"t_ProcessThis"
            };

            DefaultTableModel model = new DefaultTableModel(columnNames,0);
            table = new JTable(model);
            table.setPreferredScrollableViewportSize(new Dimension(500, 200));
            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);
        }

        public void addRow(final Object[] row) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.addRow(row);
                }
            });
        }


        public void clear() {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.setRowCount(0);
                }
            });
        }


        public JTable getTable() {
            return table;
        }

    }

    class TrackTablePanel extends JPanel implements MouseListener, KeyListener {
        private boolean DEBUG = false;
        JTable table;
        JFrame frame;
        JScrollPane scrollPane;

        public TrackTablePanel() {
            super(new GridLayout(1, 0));

            //DefaultTableModel model = new DefaultTableModel(columnNames,);
            table = trackTable.getTable();
            table.setPreferredScrollableViewportSize(new Dimension(500, 200));
            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);
            table.setRowSelectionAllowed(true);
            table.addMouseListener(this);
            table.addKeyListener(this);

            //Create the scroll pane and add the table to it.
            scrollPane = new JScrollPane(table);

            //Add the scroll pane to this panel.
            add(scrollPane);
        }

        public void showTable() {
            //Create and set up the window.
            frame = new JFrame("Tracks");
            //frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            //Create and set up the content pane.
            this.setOpaque(true); //content panes must be opaque
            frame.setContentPane(this);

            //Display the window.
            frame.pack();
            frame.setLocation(trackingGUI.getFrame().getX() + trackingGUI.getFrame().getWidth(), trackingGUI.getFrame().getY());
            frame.setVisible(true);
        }

        public void highlightSelectedTrack() {
            int rs = table.getSelectedRow();
            int r = table.convertRowIndexToModel( rs );
            float x = new Float(table.getModel().getValueAt(r, 1).toString());
            float y = new Float(table.getModel().getValueAt(r, 2).toString());
            float z = new Float(table.getModel().getValueAt(r, 3).toString());
            int t = new Integer(table.getModel().getValueAt(r, 4).toString());
            imp.setPosition(0,(int)z+1,t+1);
            Roi pr = new PointRoi(x,y);
            pr.setPosition(0,(int)z+1,t+1);
            imp.setRoi(pr);
            //log(" rs="+rs+" r ="+r+" x="+x+" y="+y+" z="+z+" t="+t);
            //log("t="+table.getModel().getValueAt(r, 5));
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            highlightSelectedTrack();
        }

        @Override
        public void mousePressed(MouseEvent e) {

        }

        @Override
        public void mouseReleased(MouseEvent e) {

        }

        @Override
        public void mouseEntered(MouseEvent e) {

        }

        @Override
        public void mouseExited(MouseEvent e) {

        }

        @Override
        public void keyTyped(KeyEvent e) {

        }

        @Override
        public void keyPressed(KeyEvent e) {

        }

        @Override
        public void keyReleased(KeyEvent e) {
            highlightSelectedTrack();
        }
    }

    public void showTrackTable(){
        if(trackTable==null) {
            IJ.showMessage("There are no tracks to show yet.");
            return;
        }
        TrackTablePanel ttp = new TrackTablePanel();
        ttp.showTable();
    }

    public void saveTrackTable(File file) {
        try{
            TableModel model = trackTable.getTable().getModel();
            FileWriter excel = new FileWriter(file);

            for(int i = 0; i < model.getColumnCount(); i++){
                excel.write(model.getColumnName(i) + "\t");
            }
            excel.write("\n");

            for(int i=0; i< model.getRowCount(); i++) {
                for(int j=0; j < model.getColumnCount(); j++) {
                    excel.write(model.getValueAt(i,j).toString()+"\t");
                }
                excel.write("\n");
            }
            excel.close();

        } catch(IOException e) { IJ.showMessage(e.toString()); }
    }

    public int addTrackStart(ImagePlus imp) {
        int x,y,t;
        Roi r = imp.getRoi();

        if(!r.getTypeAsString().equals("Point")) {
            IJ.showMessage("Please use IJ's 'Point selection tool' to mark objects.");
            return(-1);
        }

        x = r.getPolygon().xpoints[0];
        y = r.getPolygon().ypoints[0];

        int ntTracking = gui_ntTracking;
        t = imp.getT()-1;
        if(t+gui_ntTracking > imp.getNFrames()) {
            IJ.showMessage("Your Track would be longer than the movie; " +
                    "please\n - reduce 'Track length', or\n- move the time slide to an earlier time point.");
            return(-1);
        }

        totalTimePointsToBeTracked += ntTracking;
        int newTrackID = Tracks.size();
        //log("added new track start; id = "+newTrackID+"; starting [frame] = "+t+"; length [frames] = "+ntTracking);
        Tracks.add(new Track(ntTracking));
        Tracks.get(newTrackID).addLocation(new Point3D(x, y, imp.getZ()-1), t, imp.getC()-1);

        return(newTrackID);

    }

    public int addTrackStartWholeDataSet(ImagePlus imp) {
        int t;

        int ntTracking = gui_ntTracking;
        t = imp.getT()-1;

        if(t+gui_ntTracking > imp.getNFrames()) {
            IJ.showMessage("Your track would be longer than the movie!\n" +
                    "Please\n- reduce the 'Track length', or\n- move the time slider to an earlier time point.");
            return(-1);
        }

        totalTimePointsToBeTracked += ntTracking;
        int newTrackID = Tracks.size();
        //log("added new track start; id = "+newTrackID+"; starting [frame] = "+t+"; length [frames] = "+ntTracking);
        Tracks.add(new Track(ntTracking));
        Tracks.get(newTrackID).addLocation(new Point3D(0, 0, imp.getZ()-1), t, imp.getC()-1);

        return(newTrackID);

    }

    public void showCroppedTracks() {
        ImagePlus[] impA = new ImagePlus[Tracks.size()];
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        FileInfoSer[][][] infos = vss.getFileInfosSer();

        for(int i=0; i<Tracks.size(); i++) {
            Track track = Tracks.get(i);
            if (track.completed) {
                log("# showCroppedTracks: id=" + i);
                impA[i] = OpenStacksAsVirtualStack.openCroppedCenterRadiusFromInfos(imp, infos, track.getPoints3D(), gui_pCropRadii, track.getTmin(), track.getTmax());
                if (impA[i] == null) {
                    log("..cropping failed.");
                } else {
                    impA[i].setTitle("Track" + i);
                    impA[i].show();
                    impA[i].setPosition(0, (int) (impA[i].getNSlices() / 2 + 0.5), 0);
                    impA[i].resetDisplayRange();
                }
            }
        }
    }

    public void showWholeDataSetAlongTracks() {
        ImagePlus[] impA = new ImagePlus[Tracks.size()];
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        FileInfoSer[][][] infos = vss.getFileInfosSer();

        for(int i=0; i<Tracks.size(); i++) {
            Track track = Tracks.get(i);
            if (track.completed) {
                log("# showCroppedTracks: id=" + i);

                // convert track to drift
                Point3D[] trackedPoints = track.getPoints3D();
                int nt = trackedPoints.length;
                Point3D po[] = new Point3D[nt];

                double xMin=10000,yMin=10000,zMin=10000;
                for(int it=0; it<nt; it++) {
                    po[it] = trackedPoints[it].subtract(trackedPoints[0]);
                    if(po[it].getX() < xMin) xMin = po[it].getX();
                    if(po[it].getY() < yMin) yMin = po[it].getY();
                    if(po[it].getZ() < zMin) zMin = po[it].getZ();
                }
                // shift such that minimal offset is (0,0,0)
                double xMax=0,yMax=0,zMax=0;
                for(int it=0; it<nt; it++) {
                    po[it] = po[it].subtract(new Point3D(xMin,yMin,zMin));
                    if(po[it].getX() > xMax) xMax = po[it].getX();
                    if(po[it].getY() > yMax) yMax = po[it].getY();
                    if(po[it].getZ() > zMax) zMax = po[it].getZ();
                }

                // ensure that the tracked image is within the bounds
                Point3D ps = new Point3D(vss.nX-xMax,vss.nY-yMax,vss.nZ-zMax);

                impA[i] = OpenStacksAsVirtualStack.openCroppedOffsetSizeFromInfos(imp, infos, po, ps, track.getTmin(), track.getTmax());

                if (impA[i] == null) {
                    log("..cropping failed.");
                } else {
                    impA[i].setTitle("Track" + i);
                    impA[i].show();
                    impA[i].setPosition(0, (int) (impA[i].getNSlices() / 2 + 0.5), 0);
                    impA[i].resetDisplayRange();
                }
            }
        }
    }

    public void imageClosed(ImagePlus imp) {
        // currently we are not interested in this event
    }

    public void imageOpened(ImagePlus imp) {
        // currently we are not interested in this event
    }

    public void imageUpdated(ImagePlus imp) {
        if( imp == this.imp) {
            /*
            if (imp.getCanvas() == null)
                log("canvas: null");
            else
                log("canvas: found");

            if (imp.getOverlay() == null) {
                log("overlay: null");
                //showTracksOnFrame(impTR, Tracks);
            } else {
                log("overlays: " + imp.getOverlay().size());
                //showTracksOnFrame(impTR, Tracks);
            }
            */
        }
    }

    public int getNumberOfUncompletedTracks() {
        int uncomplete = 0;
        for(Track t:Tracks) {
            if(!t.completed)
                uncomplete++;
        }
        return uncomplete;
    }

    public void addTrackToOverlay(final Track track, final int i) {
        // using invokeLater to avoid that two different tracking threads change the imp overlay
        // concurrently; which could lead to disruption of the imp overlay
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                int rx = (int) gui_pCenterOfMassRadii.getX();
                int ry = (int) gui_pCenterOfMassRadii.getY();
                int rz = (int) gui_pCenterOfMassRadii.getZ();

                Roi roi;
                Overlay o = imp.getOverlay();
                if(o==null) {
                    o = new Overlay();
                    imp.setOverlay(o);
                }

                int x = (int) track.getX(i);
                int y = (int) track.getY(i);
                int z = (int) track.getZ(i);
                int c = (int) track.getC(i);
                int t = (int) track.getT(i);

                int rrx, rry;
                for(int iz=0; iz<imp.getNSlices(); iz++) {
                    rrx = Math.max(rx/(Math.abs(iz-z)+1),1);
                    rry = Math.max(ry/(Math.abs(iz-z)+1),1);
                    roi = new Roi(x - rrx, y - rry, 2 * rrx + 1, 2 * rry + 1);
                    roi.setPosition(c+1, iz+1, t+1);
                    o.add(roi);
                }
            }
        });
    }


    // todo: make one class out of the three tracking classes!!
    class TrackROI implements Runnable {
        // todo: reload more data if necessary
        int iTrack, dt, bg, iterations;
        Point3D pCenterOfMassRadii, pOffset, pLocalCenter, pSubSample;

        TrackROI(int iTrack, int dt, Point3D pSubSample, Point3D pCenterOfMassRadii, int bg, int iterations) {
            this.iTrack = iTrack;
            this.dt = dt;
            this.pSubSample = pSubSample;
            this.iterations = iterations;
            this.pCenterOfMassRadii = pCenterOfMassRadii;
            this.bg = bg;
        }

        public void run() {
            long startTime = 0, stopTime, elapsedReadingTime, elapsedProcessingTime;
            long startTimePerTimePoint, totalElapsedTime = 0;
            int mean;
            ImageStack stack;

            Track track = Tracks.get(iTrack);

            int tStart = track.getTmin();
            int channel = track.getC(0);
            int nt = track.getLength();
            Point3D pStackCenter = track.getXYZ(0);
            track.reset();
            Point3D pStackRadii = pCenterOfMassRadii.multiply(2.0);

            if (Globals.verbose) {
                log("# Registration.track3D:");
                log("iTrack: "+iTrack);
                log("tStart, tMax, dt " + tStart + "," + (tStart+nt-1) + "," + dt);
                log("pTrackStart "+pStackCenter.toString());
            }

            for (int it = tStart; it < tStart+nt; it = it + dt) {

                // get stack, ensuring that extracted stack is still within bounds
                pStackCenter = OpenStacksAsVirtualStack.curatePosition(imp, pStackCenter, pStackRadii);
                startTime = System.currentTimeMillis();
                stack = vss.getCubeByTimeCenterAndRadii(it, channel, pSubSample, pStackCenter, pStackRadii).getStack();
                stopTime = System.currentTimeMillis();
                elapsedReadingTime = stopTime - startTime;

                // compute center of mass (in zero-based local stack coordinates)
                startTime = System.currentTimeMillis();
                mean = computeMean16bit(stack);
                pLocalCenter = iterativeCenterOfMass16bit(stack, mean, pCenterOfMassRadii, iterations);
                // correct for the sub-sampling
                pLocalCenter = multiplyPoint3dComponents(pLocalCenter, pSubSample);
                stopTime = System.currentTimeMillis();
                elapsedProcessingTime = stopTime - startTime;
                //log("Computed center of mass [ms]: " + elapsedTime);

                // compute offset to zero-based center of stack
                pOffset = pLocalCenter.subtract(pStackRadii);
                //pOffset = pOffset.subtract(pOnes);

                // update time-points, using linear interpolation
                for (int j = 0; j < dt; j++) {
                    if ((it + j) < imp.getNFrames()) {

                        Point3D p = pStackCenter.add(pOffset.multiply((j + 1.0) / dt));

                        //
                        // Add to track
                        // - thread safe, because only this thread is accessing this particular track
                        //
                        track.addLocation(p, it + j, channel);

                        //
                        // Update global track table and imp.overlay
                        // - thread safe, because both internally use SwingUtilities.invokeLater
                        //
                        trackTable.addRow(new Object[]{
                                String.format("%1$04d",iTrack)+"_"+String.format("%1$05d",it),
                                (float)p.getX(), (float)p.getY(), (float)p.getZ(), it, iTrack
                                //totalElapsedTime, elapsedReadingTime, elapsedProcessingTime
                        });

                        addTrackToOverlay(track, it + j - tStart);


                    }
                }

                // update next center using Brownian motion model (position of next is same as this)
                // todo:
                // - also have a linear motion model for the update, i.e. add pOffset*2
                pStackCenter = pStackCenter.add(pOffset);


                // show progress
                int n = totalTimePointsTracked.addAndGet(dt);
                if( (System.currentTimeMillis() > trackStatsReportDelay+trackStatsLastReport)
                        || (n==totalTimePointsToBeTracked))
                    {
                    trackStatsLastReport = System.currentTimeMillis();

                    long dtt = System.currentTimeMillis()-trackStatsLastTrackStarted;
                    int dn = n - trackStatsTotalPointsTrackedAtLastStart;
                    int nToGo = totalTimePointsToBeTracked - n;
                    float speed = (float)1.0*dn/dtt*1000;
                    float remainingTime = (float)1.0*nToGo/speed;

                    Globals.threadlog(
                            "progress = "+n+"/"+totalTimePointsToBeTracked+
                            "; speed [n/s] = "+String.format("%.2g",speed)+
                            "; remaining [s] = "+String.format("%.2g",remainingTime)+
                            "; reading [ms] = "+elapsedReadingTime+
                            "; processing [ms] = "+elapsedProcessingTime
                    );
                }

                if(Globals.verbose) {
                    log("Read data [ms]: " + elapsedReadingTime);
                    log("Read data [ms]: " + elapsedReadingTime);
                    log("Reading speed [MB/s]: " + stack.getHeight() * stack.getWidth() * stack.getSize() * 2 / ((elapsedReadingTime + 0.001) * 1000));
                    log("Detected shift [pixel]:" +
                            " x:" + String.format("%.2g", pOffset.getX()) +
                            " y:" + String.format("%.2g", pOffset.getY()) +
                            " z:" + String.format("%.2g", pOffset.getZ()));
                }

            }

            //Globals.threadlog("track id = "+iTrack+"; nt [frames] = "+nt+"; completed.");
            track.completed = true;
            return;
        }
    }

    class TrackAll implements Runnable {
        int iTrack, dt, bg;
        Point3D pSubSample;

        TrackAll(int iTrack, Point3D pSubSample, int gui_tSubSample, int bg) {
            this.iTrack = iTrack;
            this.dt = gui_tSubSample;
            this.pSubSample = pSubSample;
            this.bg = bg;
        }

        public void run() {
            long startTime, stopTime, elapsedReadingTime, elapsedProcessingTime;
            ImagePlus imp0, imp1;
            Point3D pShift = new Point3D(0,0,0);
            Point3D pCenter0 = new Point3D(0,0,0);
            Point3D pCenter1 = new Point3D(0,0,0);
            int mean;

            Track track = Tracks.get(iTrack);

            int tStart = track.getTmin();
            int channel = track.getC(0);
            int nt = track.getLength();
            track.reset();


            if (Globals.verbose) {
                log("# Registration.TrackWholeDataSet:");
                log("iTrack: "+iTrack);
                log("tStart, tMax, dt, dz " + tStart + "," + (tStart+nt-1) + "," + dt );
            }

            //
            // init first time-point
            //

            // get  image
            imp0 = vss.getFullFrame(tStart, channel, pSubSample);

            // compute center of mass above background (=mean)
            startTime = System.currentTimeMillis();
            mean = computeMean16bit(imp0.getStack());
            pCenter0 = computeCenter16bit(imp0.getStack(), mean, new Point3D(0.0, 0.0, 0.0), new Point3D(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
            elapsedProcessingTime = System.currentTimeMillis() - startTime;

            // correct for sub-sampling
            pCenter0 = multiplyPoint3dComponents(pCenter0, pSubSample);

            // subtract background for correlation tracking
            if(gui_trackingMethod.equals("correlation")) {
                IJ.run(imp0, "Subtract...", "value="+mean);
            }


            //
            // compute shifts
            //

            boolean finish = false;
            int itMax = tStart + nt - 1;
            int itPrevious = tStart;
            int itNow;
            int itMaxUpdate;

            for (int it = tStart + dt; it < tStart + nt + dt; it = it + dt) {

                itNow = it;

                if(itNow >= itMax) {
                    // due to the sub-sampling in t the addition of dt
                    // caused the frame to be outside of
                    // the tracking range => load the last frame and adjust dt
                    // (this happens because nt might not be divisible by dt)
                    itNow = itMax;
                    finish = true;
                }

                // get next image
                startTime = System.currentTimeMillis();
                imp1 = vss.getFullFrame(itNow, channel, pSubSample);
                stopTime = System.currentTimeMillis();
                elapsedReadingTime = stopTime - startTime;

                if (gui_trackingMethod.equals("correlation")) {

                    // compute shift
                    startTime = System.currentTimeMillis();
                    IJ.run(imp1, "Subtract...", "value="+computeMean16bit(imp1.getStack()));
                    pShift = computeShift16bitUsingPhaseCorrelation(imp0, imp1);
                    stopTime = System.currentTimeMillis();
                    elapsedProcessingTime = stopTime - startTime;

                    // correct for sub-sampling
                    pShift = multiplyPoint3dComponents(pShift, pSubSample);
                    pCenter1 = pCenter0.add(pShift);

                } else if (gui_trackingMethod == "center of mass") {

                    // compute center of mass
                    // ...using the mean of the stack as threshold
                    startTime = System.currentTimeMillis();
                    pCenter1 = computeCenter16bit(imp1.getStack(), computeMean16bit(imp1.getStack()), new Point3D(0.0, 0.0, 0.0), new Point3D(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
                    stopTime = System.currentTimeMillis();
                    elapsedProcessingTime = stopTime - startTime;

                    // correct for the sub-sampling in z
                    pCenter1 = multiplyPoint3dComponents(pCenter1, pSubSample);

                    // compute shift
                    pShift = pCenter1.subtract(pCenter0);

                }

                // compute time-points between this and the previous one (inclusive)
                // using linear interpolation

                itMaxUpdate = itNow - 1;
                if(finish) itMaxUpdate = itNow; // include last data point

                for (int itUpdate = itPrevious; itUpdate <= itMaxUpdate; itUpdate++) {

                    Point3D p;
                    p = pCenter0.add(pShift.multiply((double)(itUpdate-itPrevious)/(double)(itNow-itPrevious)));

                    //
                    // Add to track
                    // - thread safe, because only this thread is accessing this particular track
                    //
                    track.addLocation(p, itUpdate, channel);


                    // Update global track table and imp.overlay
                    // - thread safe, because both internally use SwingUtilities.invokeLater
                    //
                    trackTable.addRow(new Object[]{
                            String.format("%1$04d", iTrack) + "_" + String.format("%1$05d", itUpdate ),
                            (float) p.getX(), (float) p.getY(), (float) p.getZ(), itUpdate, iTrack
                            //totalElapsedTime, elapsedReadingTime, elapsedProcessingTime
                    });

                    addTrackToOverlay(track, itUpdate - tStart);

                }
                itPrevious = itNow;

                //
                // show progress
                //
                int n = totalTimePointsTracked.addAndGet(dt);
                if( (System.currentTimeMillis() > trackStatsReportDelay+trackStatsLastReport)
                        || (n==totalTimePointsToBeTracked))
                {
                    trackStatsLastReport = System.currentTimeMillis();

                    long dtt = System.currentTimeMillis()-trackStatsLastTrackStarted;
                    int dn = n - trackStatsTotalPointsTrackedAtLastStart;
                    int nToGo = totalTimePointsToBeTracked - n;
                    float speed = (float)1.0*dn/dtt*1000;
                    float remainingTime = (float)1.0*nToGo/speed;

                    Globals.threadlog(
                            "progress = "+n+"/"+totalTimePointsToBeTracked+
                                    "; speed [n/s] = "+String.format("%.2g",speed)+
                                    "; remaining [s] = "+String.format("%.2g",remainingTime)+
                                    "; reading [ms] = "+elapsedReadingTime+
                                    "; processing [ms] = "+elapsedProcessingTime
                    );
                }

                if(Globals.verbose) {
                    log("Read data [ms]: " + elapsedReadingTime);
                    log("Read data [ms]: " + elapsedReadingTime);
                    log("Reading speed [MB/s]: " + imp0.getHeight() * imp0.getWidth() * imp0.getNSlices() * 2 / ((elapsedReadingTime + 0.001) * 1000));
                    log("New center [pixel]:" +
                            " x:" + String.format("%d", pCenter1.getX()) +
                            " y:" + String.format("%d", pCenter1.getY()) +
                            " z:" + String.format("%d", pCenter1.getZ()));
                    log(" " + pShift.toString());

                }

                // update reference image
                imp0 = imp1;

                // update reference point
                pCenter0 = pCenter1;

                if(finish) break;

            }

            track.completed = true;
            return;
        }
    }

    class Tracking implements Runnable {
        int iTrack, dt, bg, iterations;
        boolean objectTracking = false;
        Point3D pCenterOfMassRadii, pOffset, pLocalCenter, pSubSample;

        Tracking(int iTrack, Point3D pSubSample, Point3D pCenterOfMassRadii, int gui_tSubSample, int bg) {
            this.iTrack = iTrack;
            this.dt = gui_tSubSample;
            this.pSubSample = pSubSample;
            this.pCenterOfMassRadii = pCenterOfMassRadii;
            if(pCenterOfMassRadii!=null) this.objectTracking = true;
            this.bg = bg;
        }

        public void run() {
            long startTime, stopTime, elapsedReadingTime, elapsedProcessingTime;
            ImagePlus imp0, imp1;
            Point3D pShift = new Point3D(0,0,0);
            Point3D pCenter0 = new Point3D(0,0,0);
            Point3D pCenter1 = new Point3D(0,0,0);
            Point3D pStackRadii = null;

            Track track = Tracks.get(iTrack);
            int tStart = track.getTmin();
            int channel = track.getC(0);
            int nt = track.getLength();
            track.reset();


            //
            // init first time-point
            //

            if(objectTracking) {

                //
                // get selected track start
                //
                pCenter0 = track.getXYZ(0);
                pStackRadii = pCenterOfMassRadii.multiply(2.0);

                //
                // read data
                //

                // todo: implement x,y sub-sampling in getCubeByTimeCenterAndRadii
                pSubSample = new Point3D(1,1,pSubSample.getZ());
                // todo: maybe it is better to load a smaller part of the data instead?
                pCenter0 = OpenStacksAsVirtualStack.curatePosition(imp, pCenter0, pStackRadii);
                imp0 = vss.getCubeByTimeCenterAndRadii(tStart, channel, pSubSample, pCenter0, pStackRadii);

                //
                // compute center of mass (in zero-based local stack coordinates)
                //

                startTime = System.currentTimeMillis();
                pLocalCenter = iterativeCenterOfMass16bit(imp0.getStack(), computeMean16bit(imp0.getStack()), pCenterOfMassRadii, iterations);
                elapsedProcessingTime = System.currentTimeMillis() - startTime;

                // correct for the sub-sampling
                pLocalCenter = multiplyPoint3dComponents(pLocalCenter, pSubSample);

                // compute offset to zero-based center of stack
                pOffset = pLocalCenter.subtract(pStackRadii);

                // replaced the clicked pCenter0 by the center-of-mass
                pCenter0 = pCenter0.add(pOffset);

                // compute position for loading the object in the next time point
                // ..as don't know anything about the motions now, the same position is the best guess
                pCenter1 = pCenter0;


            } else {

                // whole image tracking
                imp0 = vss.getFullFrame(tStart, channel, pSubSample);

                // compute center of mass
                startTime = System.currentTimeMillis();
                pCenter0 = computeCenter16bit(imp0.getStack(), computeMean16bit(imp0.getStack()), new Point3D(0.0, 0.0, 0.0), new Point3D(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
                elapsedProcessingTime = System.currentTimeMillis() - startTime;

                // correct for sub-sampling
                pCenter0 = multiplyPoint3dComponents(pCenter0, pSubSample);

            }

            // subtract background for correlation tracking
            if(gui_trackingMethod.equals("correlation")) {
                IJ.run(imp0, "Subtract...", "value="+computeMean16bit(imp0.getStack()));
            }

            //
            // compute shifts for all following time-points
            //

            boolean finish = false;
            int itMax = tStart + nt - 1;
            int itPrevious = tStart;
            int itNow;
            int itMaxUpdate;

            for (int it = tStart + dt; it < tStart + nt + dt; it = it + dt) {

                itNow = it;

                if(itNow >= itMax) {
                    // due to the sub-sampling in t the addition of dt
                    // can cause the frame to be outside of
                    // the tracking range => load the last frame
                    itNow = itMax;
                    finish = true;
                }

                //
                // get next image
                //

                startTime = System.currentTimeMillis();
                if(objectTracking) {
                    pCenter1 = OpenStacksAsVirtualStack.curatePosition(imp, pCenter1, pStackRadii);
                    imp1 = vss.getCubeByTimeCenterAndRadii(itNow, channel, pSubSample, pCenter1, pStackRadii);
                } else {
                    imp1 = vss.getFullFrame(itNow, channel, pSubSample);
                }
                elapsedReadingTime = System.currentTimeMillis() - startTime;


                if (gui_trackingMethod == "correlation") {

                    // compute shift on mean subtracted image
                    startTime = System.currentTimeMillis();
                    IJ.run(imp1, "Subtract...", "value="+computeMean16bit(imp1.getStack()));
                    pShift = computeShift16bitUsingPhaseCorrelation(imp0, imp1);
                    stopTime = System.currentTimeMillis();
                    elapsedProcessingTime = stopTime - startTime;

                    // correct for sub-sampling
                    pShift = multiplyPoint3dComponents(pShift, pSubSample);
                    pCenter1 = pCenter0.add(pShift);

                } else if (gui_trackingMethod == "center of mass") {

                    // compute center of mass
                    // ...using the mean of the stack as threshold
                    startTime = System.currentTimeMillis();
                    pCenter1 = computeCenter16bit(imp1.getStack(), computeMean16bit(imp1.getStack()), new Point3D(0.0, 0.0, 0.0), new Point3D(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
                    stopTime = System.currentTimeMillis();
                    elapsedProcessingTime = stopTime - startTime;

                    // correct for the sub-sampling in z
                    pCenter1 = multiplyPoint3dComponents(pCenter1, pSubSample);

                    // compute shift
                    pShift = pCenter1.subtract(pCenter0);

                }

                // compute time-points between this and the previous one (inclusive)
                // using linear interpolation

                itMaxUpdate = itNow - 1;
                if(finish) itMaxUpdate = itNow; // include last data point

                for (int itUpdate = itPrevious; itUpdate <= itMaxUpdate; itUpdate++) {

                    Point3D p;
                    p = pCenter0.add(pShift.multiply((double)(itUpdate-itPrevious)/(double)(itNow-itPrevious)));

                    //
                    // Add to track
                    // - thread safe, because only this thread is accessing this particular track
                    //
                    track.addLocation(p, itUpdate, channel);


                    // Update global track table and imp.overlay
                    // - thread safe, because both internally use SwingUtilities.invokeLater
                    //
                    trackTable.addRow(new Object[]{
                            String.format("%1$04d", iTrack) + "_" + String.format("%1$05d", itUpdate ),
                            (float) p.getX(), (float) p.getY(), (float) p.getZ(), itUpdate, iTrack
                            //totalElapsedTime, elapsedReadingTime, elapsedProcessingTime
                    });

                    addTrackToOverlay(track, itUpdate - tStart);

                }
                itPrevious = itNow;

                //
                // show progress
                //
                int n = totalTimePointsTracked.addAndGet(dt);
                if( (System.currentTimeMillis() > trackStatsReportDelay+trackStatsLastReport)
                        || (n==totalTimePointsToBeTracked))
                {
                    trackStatsLastReport = System.currentTimeMillis();

                    long dtt = System.currentTimeMillis()-trackStatsLastTrackStarted;
                    int dn = n - trackStatsTotalPointsTrackedAtLastStart;
                    int nToGo = totalTimePointsToBeTracked - n;
                    float speed = (float)1.0*dn/dtt*1000;
                    float remainingTime = (float)1.0*nToGo/speed;

                    Globals.threadlog(
                            "progress = "+n+"/"+totalTimePointsToBeTracked+
                                    "; speed [n/s] = "+String.format("%.2g",speed)+
                                    "; remaining [s] = "+String.format("%.2g",remainingTime)+
                                    "; reading [ms] = "+elapsedReadingTime+
                                    "; processing [ms] = "+elapsedProcessingTime
                    );
                }

                if(Globals.verbose) {
                    log("Read data [ms]: " + elapsedReadingTime);
                    log("Read data [ms]: " + elapsedReadingTime);
                    log("Reading speed [MB/s]: " + imp0.getHeight() * imp0.getWidth() * imp0.getNSlices() * 2 / ((elapsedReadingTime + 0.001) * 1000));
                    log("New center [pixel]:" +
                            " x:" + String.format("%d", pCenter1.getX()) +
                            " y:" + String.format("%d", pCenter1.getY()) +
                            " z:" + String.format("%d", pCenter1.getZ()));
                    log(" " + pShift.toString());

                }

                // update reference image
                imp0 = imp1;

                // update reference point
                pCenter0 = pCenter1;

                if(finish) break;

            }

            track.completed = true;

            return;

        }

    }

    public Point3D multiplyPoint3dComponents(Point3D p0, Point3D p1) {

        double x = p0.getX() * p1.getX();
        double y = p0.getY() * p1.getY();
        double z = p0.getZ() * p1.getZ();

        return (new Point3D(x,y,z));

    }

    public Point3D iterativeCenterOfMass16bit(ImageStack stack, int bg, Point3D radii, int iterations) {
        Point3D pMin, pMax;

        Point3D pCenter = new Point3D(stack.getWidth()/2+0.5,stack.getHeight()/2+0.5,stack.getSize()/2+0.5);
        //log(""+radii.toString());
        //log(""+pCenter.toString());

        for(int i=0; i<iterations; i++) {
            //log("# Iteration "+i);
            pMin = pCenter.subtract(radii);
            pMax = pCenter.add(radii);
            pCenter = computeCenter16bit(stack, bg, pMin, pMax);
            //log("iterativeCenterOfMass16bit: center: "+pCenter.toString());
        }
        return(pCenter);
    }

    public Point3D computeCenter16bit(ImageStack stack, int bg, Point3D pMin, Point3D pMax) {

        //long startTime = System.currentTimeMillis();
        double sum = 0.0, xsum = 0.0, ysum = 0.0, zsum = 0.0;
        int i, v;
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();
        int xmin = 0 > (int) pMin.getX() ? 0 : (int) pMin.getX();
        int xmax = (width-1) < (int) pMax.getX() ? (width-1) : (int) pMax.getX();
        int ymin = 0 > (int) pMin.getY() ? 0 : (int) pMin.getY();
        int ymax = (height-1) < (int) pMax.getY() ? (height-1) : (int) pMax.getY();
        int zmin = 0 > (int) pMin.getZ() ? 0 : (int) pMin.getZ();
        int zmax = (depth-1) < (int) pMax.getZ() ? (depth-1) : (int) pMax.getZ();

        // compute one-based, otherwise the numbers at x=0,y=0,z=0 are lost for the center of mass
        // todo: take the method if-statement of out of the loop for increased speed?!

        if(bg>0) {

            //
            // ignore values <bg
            //

            if (gui_centeringMethod == "center of mass") {
                for (int z = zmin + 1; z <= zmax + 1; z++) {
                    ImageProcessor ip = stack.getProcessor(z);
                    short[] pixels = (short[]) ip.getPixels();
                    for (int y = ymin + 1; y <= ymax + 1; y++) {
                        i = (y - 1) * width + xmin; // zero-based location in pixel array
                        for (int x = xmin + 1; x <= xmax + 1; x++) {
                            v = pixels[i] & 0xffff;
                            if (v >= bg) {
                                sum += v;
                                xsum += x * v;
                                ysum += y * v;
                                zsum += z * v;
                            }
                            i++;
                        }
                    }
                }
            }

            if (gui_centeringMethod == "centroid") {
                for (int z = zmin + 1; z <= zmax + 1; z++) {
                    ImageProcessor ip = stack.getProcessor(z);
                    short[] pixels = (short[]) ip.getPixels();
                    for (int y = ymin + 1; y <= ymax + 1; y++) {
                        i = (y - 1) * width + xmin; // zero-based location in pixel array
                        for (int x = xmin + 1; x <= xmax + 1; x++) {
                            v = pixels[i] & 0xffff;
                            if (v >= bg) {
                                sum += 1;
                                xsum += x;
                                ysum += y;
                                zsum += z;
                            }
                            i++;
                        }
                    }
                }
            }

        } else {

            //
            // no background
            //

            if (gui_centeringMethod == "center of mass") {
                for (int z = zmin + 1; z <= zmax + 1; z++) {
                    ImageProcessor ip = stack.getProcessor(z);
                    short[] pixels = (short[]) ip.getPixels();
                    for (int y = ymin + 1; y <= ymax + 1; y++) {
                        i = (y - 1) * width + xmin; // zero-based location in pixel array
                        for (int x = xmin + 1; x <= xmax + 1; x++) {
                            v = pixels[i] & 0xffff;
                            sum += v;
                            xsum += x * v;
                            ysum += y * v;
                            zsum += z * v;
                            i++;
                        }
                    }
                }
            }
        }

        // computation is one-based; result should be zero-based
        double xCenter = (xsum / sum) - 1;
        double yCenter = (ysum / sum) - 1;
        double zCenter = (zsum / sum) - 1;

        //long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("center of mass in [ms]: " + elapsedTime);

        return(new Point3D(xCenter,yCenter,zCenter));
    }

    // ?? why is background subtraction necessary ?
    public Point3D computeShift16bitUsingPhaseCorrelation(ImagePlus imp0, ImagePlus imp1) {
        PhaseCorrelation phc = new PhaseCorrelation(ImagePlusAdapter.wrap(imp1), ImagePlusAdapter.wrap(imp0), 5, true);
        phc.process();
        int[] shift = phc.getShift().getPosition();
        return(new Point3D(shift[0],shift[1],shift[2]));
    }

    public int computeMean16bit(ImageStack stack) {

        //long startTime = System.currentTimeMillis();
        double sum = 0.0;
        int i;
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();
        int xMin = 0;
        int xMax = (width-1);
        int yMin = 0;
        int yMax = (height-1);
        int zMin = 0;
        int zMax = (depth-1);

        for(int z=zMin; z<=zMax; z++) {
            short[] pixels = (short[]) stack.getProcessor(z+1).getPixels();
            for (int y = yMin; y<=yMax; y++) {
                i = y * width + xMin;
                for (int x = xMin; x <= xMax; x++) {
                    sum += (pixels[i] & 0xffff);
                    i++;
                }
            }
        }

        return((int) sum/(width*height*depth));

    }

    public Point3D maxLoc16bit(ImageStack stack) {
        long startTime = System.currentTimeMillis();
        int vmax = 0, xmax = 0, ymax = 0, zmax = 0;
        int i, v;
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();

        for(int z=1; z <= depth; z++) {
            ImageProcessor ip = stack.getProcessor(z);
            short[] pixels = (short[]) ip.getPixels();
            i = 0;
            for (int y = 1; y <= height; y++) {
                i = (y-1) * width;
                for (int x = 1; x <= width; x++) {
                    v = pixels[i] & 0xffff;
                    if (v > vmax) {
                        xmax = x;
                        ymax = y;
                        zmax = z;
                        vmax = v;
                    }
                    i++;
                }
            }
        }

        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("center of mass in [ms]: " + elapsedTime);

        return(new Point3D(xmax,ymax,zmax));
    }

}




