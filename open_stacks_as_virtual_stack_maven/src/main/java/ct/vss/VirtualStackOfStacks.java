package ct.vss;

/**
 * Created by tischi on 27/10/16.
 */

import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;

import static ij.IJ.log;




// todo: replace all == with "equals"

/**
 This class represents an array of disk-resident image stacks.
 */
public class VirtualStackOfStacks extends ImageStack {
    int nSlices;
    int nX, nY, nZ, nC, nT;
    FileInfoSer[][][] infos;  // c, t, z
    String fileType = "tiff"; // h5
    String directory = "";
    String[] channelFolders;
    String[][] fileList;
    String h5DataSet;

    /** Creates a new, empty virtual stack of required size */
    public VirtualStackOfStacks(String directory, String[] channelFolders, String[][] fileList, int nC, int nT, String fileType, String h5DataSet) {
        super();

        this.directory = directory;
        this.channelFolders = channelFolders;
        this.fileList = fileList;
        this.nC = nC;
        this.nT = nT;
        this.fileType = fileType;
        this.infos = new FileInfoSer[nC][nT][];
        this.h5DataSet = h5DataSet;

        if(Globals.verbose) {
            logStatus();
        }

    }

    public VirtualStackOfStacks(String directory, FileInfoSer[][][] infos) {
        super();

        this.infos = infos;
        this.directory = directory;
        nC = infos.length;
        nT = infos[0].length;

        if(infos[0][0][0].isCropped) {
            nX = (int) infos[0][0][0].pCropSize[0];
            nY = (int) infos[0][0][0].pCropSize[1];
            nZ = (int) infos[0][0][0].pCropSize[2];
        } else {
            nX = (int) infos[0][0][0].width;
            nY = (int) infos[0][0][0].height;
            nZ = (int) infos[0][0].length;
        }

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

    public String getDirectory() {
        return directory;
    }

    public int numberOfUnparsedFiles() {
        int numberOfUnparsedFiles = 0;
        for(int c = 0; c < nC; c++ )
            for(int t = 0; t < nT; t++)
                if (infos[c][t] == null)
                    numberOfUnparsedFiles++;

        return numberOfUnparsedFiles;
    }

    /** Adds an image stack from file infos */
    public void setStackFromFile(int t, int c) {
        FileInfo[] info = null;
        FileInfo fi = null;
        FileInfoSer[] infoSer = null;

        String ctPath = directory + channelFolders[c] + "/" + fileList[c][t];
        try {
            if (fileType == "tif") {

                long startTime = System.currentTimeMillis();
                FastTiffDecoder ftd = new FastTiffDecoder(directory + channelFolders[c], fileList[c][t]);
                info = ftd.getTiffInfo();

                // first file
                if (t == 0 && c == 0) {

                    if (Globals.verbose) {
                        log("IFD reading time [ms]: " + (System.currentTimeMillis() - startTime));
                        log("directory: "+directory);
                        log("channelFolders[c]: "+channelFolders[c]);
                        log("c: " + c + "; t: " + t + "; file: " + fileList[c][t]);
                        log("info.length " + info.length);
                        log("info[0].compression " + info[0].compression);
                        log("info[0].stripLengths.length " + info[0].stripLengths.length);
                        log("info[0].rowsPerStrip " + info[0].rowsPerStrip);
                    }
                    fi = info[0];
                    if (fi.nImages > 1) {
                        nZ = fi.nImages;
                        fi.nImages = 1;
                    } else {
                        nZ = info.length;
                    }
                    nX = fi.width;
                    nY = fi.height;
                    nSlices = nC*nT*nZ;


                } else {

                    if (Globals.verbose) {
                        log("IFD reading time [ms]: " + (System.currentTimeMillis() - startTime));
                        log("c:" + c + "; t:" + t + "; file:" + fileList[c][t]);
                        log("info.length " + info.length);
                        log("info[0].compression " + info[0].compression);
                        log("info[0].stripLengths.length " + info[0].stripLengths.length);
                        log("info[0].rowsPerStrip " + info[0].rowsPerStrip);
                    }

                }

                // convert ij.io.FileInfo[] to FileInfoSer[]
                infoSer = new FileInfoSer[nZ];
                for (int i = 0; i < nZ; i++) {
                    infoSer[i] = new FileInfoSer(info[i]);
                    infoSer[i].directory = channelFolders[c] + "/"; // relative path to main directory
                    infoSer[i].fileTypeString = fileType;
                }

            } // tif

            if (fileType == "h5") {

                // first file
                if (t == 0 && c == 0) {
                    IHDF5Reader reader = HDF5Factory.openForReading(ctPath);
                    HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation("/" + h5DataSet);

                    nZ = (int) dsInfo.getDimensions()[0];
                    nY = (int) dsInfo.getDimensions()[1];
                    nX = (int) dsInfo.getDimensions()[2];
                    nSlices = nC*nT*nZ;

                }

                if (Globals.verbose) {
                    log("nX " + nX);
                    log("nY " + nY);
                    log("nZ " + nZ);
                }

                // construct a FileInfoSer
                // todo: this could be much leaner
                // e.g. the nX, nY and bit depth
                infoSer = new FileInfoSer[nZ];
                for (int i = 0; i < nZ; i++) {
                    infoSer[i] = new FileInfoSer();
                    infoSer[i].fileName = fileList[c][t];
                    infoSer[i].directory = channelFolders[c] + "/";
                    infoSer[i].width = nX;
                    infoSer[i].height = nY;
                    infoSer[i].bytesPerPixel = 2; // todo: how to get the bit-depth from the info?
                    infoSer[i].h5DataSet = h5DataSet;
                    infoSer[i].fileTypeString = fileType;
                }

            } // h5

        } catch(Exception e) {
            IJ.showMessage("Error: "+e.toString());

        }

        this.infos[c][t] = infoSer;
    }

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

        ImagePlus imp;


        if(Globals.verbose) {
            log("# VirtualStackOfStacks.getProcessor");
            log("requested slice [one-based]: "+(n+1));
            log("c [one-based]: "+ (c+1));
            log("z [one-based]: "+ (z+1));
            log("t [one-based]: "+ (t+1));
            log("opening file: "+directory+infos[c][t][0].directory+infos[c][t][0].fileName);
        }

        int dz = 1;
        Point3D po, ps;
        if(infos[c][t] == null) {
            //ImagePlus imp0 = IJ.getImage();
            //imp0.setPosition(1,(int)imp0.getNSlices()/2,1);
            //imp0.updateAndDraw();
            //IJ.showMessage("The file corresponding to this time point has not been analyzed yet.\n" +
            //        "Please wait...");
            setStackFromFile(t, c);
        }

        FileInfoSer fi = infos[c][t][0];

        if(fi.isCropped) {
            // load cropped slice
            po = new Point3D(fi.pCropOffset[0],fi.pCropOffset[1],fi.pCropOffset[2]+z);;
            ps = new Point3D(fi.pCropSize[0],fi.pCropSize[1],1);

        } else {
            // load full slice
            po = new Point3D(0,0,z);
            ps = new Point3D(fi.width,fi.height,1);
        }

        imp = new OpenerExtensions().openCroppedStackOffsetSize(directory, infos[c][t], dz, po, ps);

        if (imp==null) {
            log("Error: loading failed!");
            return null;
        }

        return imp.getProcessor();
    }

    public boolean isCropped() {
        return(infos[0][0][0].isCropped);
    }

    public Point3D getCropOffset() {
        return(new Point3D(infos[0][0][0].pCropOffset[0], infos[0][0][0].pCropOffset[1], infos[0][0][0].pCropOffset[2]));
    }

    public Point3D getCropSize() {
        return(new Point3D(infos[0][0][0].pCropSize[0], infos[0][0][0].pCropSize[1], infos[0][0][0].pCropSize[2]));
    }

    public ImagePlus getFullFrame(int t, int c, int dz) {
        Point3D po, ps;

        po = new Point3D(0, 0, 0);
        if(infos[0][0][0].isCropped) {
            ps = infos[0][0][0].getCropSize();
        } else {
            ps = new Point3D(nX, nY, nZ);
        }

        return(getCubeByTimeOffsetAndSize(t, c, dz, po, ps));

    }

    public ImagePlus getCubeByTimeCenterAndRadii(int t, int c, int dz, Point3D pc, Point3D pr) {

        if(Globals.verbose) {
            log("# VirtualStackOfStacks.getCroppedFrameCenterRadii");
            log("t: "+t);
            log("c: "+c);
            }

        FileInfoSer fi = infos[0][0][0];

        if(fi.isCropped) {
            // load cropped slice
            pc = pc.add(fi.getCropOffset());
        }

        if(infos[c][t] == null) {
            // file info not yet loaded => get it!
            setStackFromFile(t, c);
        }

        ImagePlus imp = new OpenerExtensions().openCroppedStackCenterRadii(directory, infos[c][t], dz, pc, pr);

        if (imp==null) {
            log("Error: loading failed!");
            return null;
        } else {
            return imp;
        }
    }

    public ImagePlus getCubeByTimeOffsetAndSize(int t, int c, int dz, Point3D po, Point3D ps) {

        if(Globals.verbose) {
            log("# VirtualStackOfStacks.getCroppedFrameOffsetSize");
            log("t: "+t);
            log("c: "+c);
        }

        FileInfoSer fi = infos[0][0][0];

        if(fi.isCropped) {
            po = po.add(fi.getCropOffset());
        }

        if(infos[c][t] == null) {
            // file info not yet loaded => get it!
            setStackFromFile(t, c);
        }

        ImagePlus imp = new OpenerExtensions().openCroppedStackOffsetSize(directory, infos[c][t], dz, po, ps);

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

