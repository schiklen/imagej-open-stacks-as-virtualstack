package ct.vss;

/**
 * Created by tischi on 27/10/16.
 */

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;

import static ij.IJ.log;

/**
 This class represents an array of disk-resident image stacks.
 */
public class VirtualStackOfStacks extends ImageStack {
    int nSlices;
    int nX, nY, nZ, nC, nT;
    protected FileInfoSer[][][] infos;  // c, t, z
    protected String fileType = "tiff"; // h5

    /** Creates a new, empty virtual stack of required size */
    public VirtualStackOfStacks(Point3D pSize, int nC, int nT, String fileType) {
        super();
        this.nX = (int)pSize.getX();
        this.nY = (int)pSize.getY();
        this.nZ = (int)pSize.getZ();
        this.nC = nC;
        this.nT = nT;
        this.fileType = fileType;
        this.infos = new FileInfoSer[nC][nT][];
        nSlices = nC*nT*nZ;

        if(Globals.verbose) {
            logStatus();
        }

    }

    public VirtualStackOfStacks(FileInfoSer[][][] infos) {
        super();

        if(infos[0][0][0].isCropped) {
            nX = (int) infos[0][0][0].pCropSize.getX();
            nY = (int) infos[0][0][0].pCropSize.getY();
            nZ = (int) infos[0][0][0].pCropSize.getZ();
        } else {
            nX = (int) infos[0][0][0].width;
            nY = (int) infos[0][0][0].height;
            nZ = (int) infos[0][0].length;
        }

        this.infos = infos;
        nC = infos.length;
        nT = infos[0].length;
        nZ = infos[0][0].length;
        nSlices = nC*nT*nZ;

        if(infos[0][0][0].fileName.endsWith(".h5"))
            this.fileType = "h5";
        if(infos[0][0][0].fileName.endsWith(".tif"))
            this.fileType = "tif";

        if(Globals.verbose) {
            logStatus();
        }

    }

    public void logStatus() {
            log("# VirtualStackOfStacks");
            log("fileType: "+fileType);
            log("x: "+nX);
            log("y: "+nY);
            log("z: "+nZ);
            log("c: "+nC);
            log("t: "+nT);
    }

    public FileInfoSer[][][] getFileInfosSer() {
        return(infos);
    }

    /** Adds an image stack.
    public void addStack(FileInfoSer[] info, int t, int c) {
        if (info==null)
            throw new IllegalArgumentException("'info' is null!");
        infos[c][t] = info;
    } */


    /** Does nothing. */
    public void addSlice(String sliceLabel, Object pixels) {
    }

    /** Does nothing.. */
    public void addSlice(String sliceLabel, ImageProcessor ip) {
    }

    /** Does noting. */
    public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
    }

    /** Does noting. */
    public void deleteSlice(int n) {
        /*
        if (n<1 || n>nSlices)
            throw new IllegalArgumentException("Argument out of range: "+n);
        if (nSlices<1)
            return;
        for (int i=n; i<nSlices; i++)
            infos[i-1] = infos[i];
        infos[nSlices-1] = null;
        nSlices--;
        */
    }

    /** Deletes the last slice in the stack. */
    public void deleteLastSlice() {
        /*if (nSlices>0)
            deleteSlice(nSlices);
            */
    }

    /** Returns the pixel array for the specified slice, were 1<=n<=nslices. */
    public Object getPixels(int n) {
        ImageProcessor ip = getProcessor(n);
        if (ip!=null)
            return ip.getPixels();
        else
            return null;
    }

    /** Assigns a pixel array to the specified slice,
     were 1<=n<=nslices. */
    public void setPixels(Object pixels, int n) {
    }

    /** Returns an ImageProcessor for the specified slice,
     were 1<=n<=nslices. Returns null if the stack is empty.
     */
    /* the n is computed by IJ assuming the czt ordering
    n = ( c + z*nC + t*nZ*nC ) + 1
    */
    public ImageProcessor getProcessor(int n) {
        // recompute c,z,t
        n -= 1;
        int c = (n % nC);
        int z = ((n-c)%(nZ*nC))/nC;
        int t = (n-c-z*nC)/(nZ*nC);

        if(Globals.verbose) {
            log("# VirtualStackOfStacks.getProcessor");
            log("requested slice [one-based]: "+(n+1));
            log("c [one-based]: "+ (c+1));
            log("z [one-based]: "+ (z+1));
            log("t [one-based]: "+ (t+1));
            log("opening file: "+infos[c][t][0].fileName);
        }

        // todo: put this decision into the OpenerExtensions

        int dz = 1;
        Point3D po, ps;

        if(infos[c][t][0].isCropped) {
            // load cropped slice
            po = infos[c][t][0].pCropOffset;
            ps = new Point3D(infos[c][t][0].pCropSize.getX(),infos[c][t][0].pCropSize.getY(),1);
        } else {
            // load full slice
            po = new Point3D(0,0,0);
            ps = new Point3D(infos[c][t][0].width,infos[c][t][0].height,1);
        }

        ImagePlus imp = new OpenerExtensions().openCroppedStackOffsetSize(infos[c][t], dz, po, ps);

        if (imp==null) {
            log("Error: loading failed!");
            return null;
        }

        return imp.getProcessor();
    }

    public ImagePlus getCroppedFrameCenterRadii(int t, int c, int dz, Point3D pc, Point3D pr) {

        if(Globals.verbose) {
            log("# VirtualStackOfStacks.getCroppedFrameCenterRadii");
            log("t: "+t);
            log("c: "+c);
            }

        ImagePlus imp = new OpenerExtensions().openCroppedStackCenterRadii(infos[c][t], dz, pc, pr);

        if (imp==null) {
            log("Error: loading failed!");
            return null;
        } else {
            return imp;
        }
    }

    public int getSize() {
        return nSlices;
    }

    public int getWidth() {
        return nX;
    }

    public int getHeight() {
        return nY;
    }

    /** Returns the file name of the Nth image. */
    public String getSliceLabel(int n) {
        //int nFile;
        //nFile = (n-1) / nZ;
        //return infos[nFile][0].fileName;
        return "slice label";
    }

    /** Returns null. */
    public Object[] getImageArray() {
        return null;
    }

    /** Does nothing. */
    public void setSliceLabel(String label, int n) {
    }

    /** Always return true. */
    public boolean isVirtual() {
        return true; // do we need this?
    }

    /** Does nothing. */
    public void trim() {
    }

}


/*
    public void deleteSlice(int n) {
        if (n<1 || n>nSlices)
            throw new IllegalArgumentException("Argument out of range: "+n);
        if (nSlices<1)
            return;
        for (int i=n; i<nSlices; i++)
            infos[i-1] = infos[i];
        infos[nSlices-1] = null;
        nSlices--;
    }

    /** Deletes the last slice in the stack.
    public void deleteLastSlice() {
        if (nSlices>0)
            deleteSlice(nSlices);
    }*/

