package ct.vss;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;
import ij.plugin.Filters3D;
import static ij.IJ.log;
import ij.plugin.frame.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;

/**
 * Created by tischi on 31/10/16.
 */

// https://imagej.nih.gov/ij/developer/source/ij/plugin/frame/ContrastAdjuster.java.html

public class Registration implements PlugIn {

    VirtualStackOfStacks vss;
    ImagePlus imp;
    public final static int MEAN=10, MEDIAN=11, MIN=12, MAX=13, VAR=14, MAXLOCAL=15; // Filters3D
    private static NonBlockingGenericDialog gd;
    private final static Point3D pOnes = new Point3D(1,1,1);
    // gui variables
    int gui_t, gui_nt, gui_bg, gui_iterations;
    Point3D gui_pStackCenter, gui_pStackRadii, gui_pCenterOfMassRadii;

    public Registration(ImagePlus imp, boolean gui) {
        this.imp = imp;
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if(vss==null) {
            throw new IllegalArgumentException("Registration only works with VirtualStackOfStacks");
        }
        this.vss = vss;
        if(gui) showDialog();
    }

    public void run(String arg) {

    }
    // todo: button: Show ROI
    public void showDialog() {
        gd = new NonBlockingGenericDialog("Registration");
        gd.addSlider("Radius center of mass x:", 0, (int) imp.getWidth() / 2, 20);
        gd.addSlider("Radius center of mass y:", 0, (int) imp.getHeight() / 2, 20);
        gd.addSlider("Radius center of mass z:", 0, (int) imp.getNSlices() / 2, 20);
        gd.addNumericField("Image loading margin factor", 2, 1);
        gd.addNumericField("Image background",100,0);
        gd.addNumericField("Iterations",6,0);
        //gd.addStringField("File Name Contains:", "");
        //((Label)theLabel).setText(""+imp.getTitle());
        Button bt = new Button("Correct current position");
        bt.addActionListener(new ActionListener() {
                                 public void actionPerformed(ActionEvent e) {
                                     if (updateGuiVariables()) {
                                         gui_nt = 0; // only 'track' current position
                                         Point3D[] points = track3D(gui_t, gui_nt, gui_pStackCenter, gui_pStackRadii, gui_pCenterOfMassRadii, gui_bg, gui_iterations);
                                         Point3D pCenter = points[gui_t];
                                         // show on image, computing back to global coordinates
                                         int rx = (int) gui_pStackRadii.getX();
                                         int ry = (int) gui_pStackRadii.getY();
                                         Roi r = new PointRoi(pCenter.getX(), pCenter.getY());
                                         Overlay o = new Overlay();
                                         Roi cropBounds = new Roi(pCenter.getX() - rx, pCenter.getY() - ry, 2 * rx + 1, 2 * ry + 1);
                                         o.add(cropBounds);
                                         rx = (int) gui_pCenterOfMassRadii.getX();
                                         ry = (int) gui_pCenterOfMassRadii.getY();
                                         Roi comBounds = new Roi(pCenter.getX() - rx, pCenter.getY() - ry, 2 * rx + 1, 2 * ry + 1);
                                         o.add(comBounds);
                                         imp.setPosition(0, ((int) pCenter.getZ() + 1), gui_t + 1);
                                         imp.deleteRoi();
                                         imp.setRoi(r);
                                         o.setLabelColor(Color.blue);
                                         imp.setOverlay(o);

                                     }
                                 }
                             });
        gd.add(bt);
        gd.showDialog();
    }

    public boolean updateGuiVariables() {

        Roi roi = imp.getRoi();

        if ((roi != null) && (roi.getPolygon().npoints == 1)) {

            // get more values from GUI
            int x = roi.getPolygon().xpoints[0];
            int y = roi.getPolygon().ypoints[0];
            int z = imp.getZ() - 1;
            gui_t = imp.getT() - 1;

            int rx = new Integer(getTextFieldTxt(gd, 0));
            int ry = new Integer(getTextFieldTxt(gd, 1));
            int rz = new Integer(getTextFieldTxt(gd, 2));
            double marginFactor = new Double(getTextFieldTxt(gd, 3));

            gui_bg = new Integer(getTextFieldTxt(gd, 4));
            gui_iterations = new Integer(getTextFieldTxt(gd, 5));
            gui_pCenterOfMassRadii = new Point3D(rx, ry, rz);
            gui_pStackRadii = gui_pCenterOfMassRadii.multiply(marginFactor);
            gui_pStackCenter = new Point3D(x, y, z);

            return(true);

        } else {

            log("No PointTool selection on image");
            return(false);
        }

    }


    public String getTextFieldTxt(GenericDialog gd, int i) {
        TextField tf = (TextField) gd.getNumericFields().get(i);
        return(tf.getText());
    }


    public Point3D[] track3D(int t, int nt, Point3D pStackCenter, Point3D pStackRadii, Point3D pCenterOfMassRadii, int bg, int iterations) {
        Point3D[] points = new Point3D[vss.nSlices];
        ImageStack stack;
        Point3D pOffset, pLocalCenter;

        for (int it = t; it <= t+nt; it++) {

            // get stack, ensuring that extracted stack is within bounds
            pStackCenter = curatePosition(pStackCenter, pStackRadii);
            stack = getImageStack(it, pStackCenter, pStackRadii);

            // compute center of mass (in zero-based local stack coordinates)
            pLocalCenter = iterativeCenterOfMass16bit(stack, bg, pCenterOfMassRadii, iterations);

            // compute offset to zero-based center of stack
            pOffset = pLocalCenter.subtract(pStackRadii);
            //pOffset = pOffset.subtract(pOnes);
            log("track3D.pOffset: "+pOffset.toString());

            // shift the global position of this stack and store to return array
            // todo:
            // - also have a linear motion model
            pStackCenter = pStackCenter.add(pOffset);
            points[it] =  pStackCenter;
        }

        log("Tracking done.");
        log("");
        return(points);
    }

    private ImageStack getImageStack(int it, Point3D p, Point3D pr) {
        //log("Registration.getImageStack p[0]: "+p[0]+" z:"+p[3]);
        long startTime = System.currentTimeMillis();
        ImageStack stack = vss.getCroppedFrameAsImagePlus(it, 0, p, pr).getStack();
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("loaded stack in [ms]: " + elapsedTime);
        //imp.show();
        return(stack);
    }

    public Point3D curatePosition(Point3D p, Point3D pr) {

        // round the values
        int x = (int) (p.getX()+0.5);
        int y = (int) (p.getY()+0.5);
        int z = (int) (p.getZ()+0.5);
        int rx = (int) pr.getX();
        int ry = (int) pr.getY();
        int rz = (int) pr.getZ();

        // make sure that the ROI stays within the image bounds
        if (x-rx < 0) x = 0;
        if (y-ry < 0) y = 0;
        if (z-rz < 0) z = 0;

        if (x+rx >= imp.getWidth()) x = imp.getWidth()-rx;
        if (y+ry >= imp.getHeight()) y = imp.getHeight()-ry;
        if (z+rz >= imp.getNSlices()) z = imp.getNSlices()-rz;

        return(new Point3D(x,y,z));
    }

    public Point3D iterativeCenterOfMass16bit(ImageStack stack, int bg, Point3D radii, int iterations) {
        Point3D pMin, pMax;

        Point3D pCenter = new Point3D(stack.getWidth()/2+0.5,stack.getHeight()/2+0.5,stack.getSize()/2+0.5);
        //log(""+radii.toString());
        //log(""+pCenter.toString());

        for(int i=0; i<iterations; i++) {
            log("# Iteration "+i);
            pMin = pCenter.subtract(radii);
            pMax = pCenter.add(radii);
            pCenter = centerOfMass16bit(stack, bg, pMin, pMax);
            log("iterativeCenterOfMass16bit: center: "+pCenter.toString());
        }
        return(pCenter);
    }

    public Point3D centerOfMass16bit(ImageStack stack, int bg, Point3D pMin, Point3D pMax) {

        long startTime = System.currentTimeMillis();
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

        Point3D pMinCorr = new Point3D(xmin,ymin,zmin);
        Point3D pMaxCorr = new Point3D(xmax,ymax,zmax);

        /*
        log("centerOfMass16bit: pMin: "+pMin.toString());
        log("centerOfMass16bit: pMax: "+pMax.toString());
        log("centerOfMass16bit: pMinCorr: "+pMinCorr.toString());
        log("centerOfMass16bit: pMaxCorr: "+pMaxCorr.toString());
        */

        // compute one-based, otherwise the numbers at x=0,y=0,z=0 are lost for the center of mass
        for(int z=zmin+1; z<=zmax+1; z++) {
            ImageProcessor ip = stack.getProcessor(z);
            short[] pixels = (short[]) ip.getPixels();
            i = 0;
            for (int y = ymin+1; y<=ymax+1; y++) {
                i = (y-1) * width + xmin; // zero-based location in pixel array
                for (int x = xmin+1; x<=xmax+1; x++) {
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
        // computation is one-based; result should be zero-based
        double xCenterOfMass = (xsum / sum) - 1;
        double yCenterOfMass = (ysum / sum) - 1;
        double zCenterOfMass = (zsum / sum) - 1;

        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("center of mass in [ms]: " + elapsedTime);

        return(new Point3D(xCenterOfMass,yCenterOfMass,zCenterOfMass));
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


// http://imagej.1557.x6.nabble.com/Getting-x-y-coordinates-of-the-multi-point-tool-td4490440.html


class RegistrationDialog extends NonBlockingGenericDialog {
    ImagePlus imp;

    public RegistrationDialog(ImagePlus imp) {
        super("Registration");
        this.imp = imp;
    }



    protected void setup() {
        //setPositionInfo();
    }

    public void itemStateChanged(ItemEvent e) {
        log(""+e);
    }

    public void keyTyped(KeyEvent e) {
        log(""+e.getKeyChar());
    }


    public void textValueChanged(TextEvent e) {
        //setStackInfo();
    }

    //public void actionPerformed(java.awt.event.ActionEvent e){
    //    log(""+e);
    //}

    //void setPositionInfo() {
    //    ((Label)theLabel).setText(""+imp.getTitle());
   // }


}

