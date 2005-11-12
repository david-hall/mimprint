/* IconLoader.java
 *
 * Jim McBeath, October 31, 2005
 */

package jimmc.jiviewer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.Toolkit;
import java.io.File;
import javax.swing.ImageIcon;

/** A background thread to load image icons.
 */
public class IconLoader extends Thread {
    private ImageLister lister;

    public IconLoader(ImageLister lister) {
        this.lister = lister;
    }

    public void run() {
//System.out.println("running iconLoader");
        while (true) {  //keep running unilt the app exits
            FileInfo[] fileInfos = getFileInfoList();
            int updatedCount = loadFileInfos(fileInfos);
            if (updatedCount==0)
                waitForMoreIcons();
        }
    }

    /** Call this from the lister to notify us that
     * we should go look for more images to load.
     */
    public synchronized void moreIcons() {
//System.out.println("moreIcons");
        this.notify();
    }

    //Wait for the lister to tell us we have more work.
    private synchronized void waitForMoreIcons() {
        //Before we wait, run throught the list and make sure there
        //are not any new icons that snuck in there after our last
        //pass through the list.
        FileInfo[] fileInfos = getFileInfoList();
        for (int i=0; fileInfos!=null && i<fileInfos.length; i++) {
            FileInfo fi = fileInfos[i];
            if (needsIcon(fi))
                return;             //found one that needs loading
        }
//System.out.println("waitForMoreIcons");
        try {
            this.wait();
        } catch (InterruptedException ex) {
            System.out.println("Wait interrupted");
        }
//System.out.println("waitForMoreIcons done");
    }

    //Notify the lister that it should update the display on
    //an item in its list.
    private boolean notifyLister(FileInfo[] fileInfos, int n) {
//System.out.println("notifyLister");
        return lister.iconLoaded(fileInfos,n);
    }

    private FileInfo[] getFileInfoList() {
        return lister.getFileInfoList();
    }

    //Load all icons that are ready to be loaded
    private int loadFileInfos(FileInfo[] fileInfos) {
//System.out.println("loadFileInfos");
        if (fileInfos==null)
            return 0;
        int updatedCount = 0;
        for (int i=0; i<fileInfos.length; i++) {
            FileInfo fi = fileInfos[i];
            if (!needsIcon(fi))
                continue;
            fi.icon = getFileIcon(fi,i);
            if (!notifyLister(fileInfos,i)) {
                //The list in lister changed, don't
                //bother loading the rest of these icons
                return updatedCount;
            }
            updatedCount++;
        }
        return updatedCount;
    }

    private boolean needsIcon(FileInfo fi) {
        if (fi==null)
            return false;       //that list item not yet visible
        if (fi.icon!=null)
            return false;       //icon already loaded
        if (fi.type!=FileInfo.IMAGE && fi.type!=FileInfo.JIV)
            return false;       //only load icons for image files and our own files
        return true;
    }

    //Load the icon for the specified file.
    private ImageIcon getFileIcon(FileInfo fileInfo, int index) {
        String filename = fileInfo.getPath();
        Toolkit toolkit = lister.getToolkit();
        if (filename.toLowerCase().endsWith(".jiv")) {
            PageLayout pageLayout = new PageLayout();
            pageLayout.loadLayoutTemplate(new File(filename));
            //TODO - check to make sure it got loaded correctly,
            //catch exceptions and put in an error icon
            String desc = pageLayout.getDescription();
            if (desc!=null) {
                fileInfo.text = desc;
                fileInfo.html = lister.getFileTextInfo(fileInfo,index,true);
                    //update html if text got updated
            }
            BufferedImage image = new BufferedImage(ImageLister.ICON_SIZE,
                    ImageLister.ICON_SIZE,BufferedImage.TYPE_BYTE_INDEXED);
            Graphics2D g2 = image.createGraphics();
            //Scale the layout to fit in the imate
            g2.setColor(Color.white);   //we use gray in the real layout background,
                    //but white looks better here
            g2.fillRect(0,0,ImageLister.ICON_SIZE,ImageLister.ICON_SIZE); //clear to background
            ImagePage.scaleAndTranslate(g2,
                    pageLayout.getPageWidth(),pageLayout.getPageHeight(),
                    ImageLister.ICON_SIZE,ImageLister.ICON_SIZE);
            g2.setColor(Color.white);
            g2.fillRect(0,0,pageLayout.getPageWidth(),pageLayout.getPageHeight());
            g2.setColor(Color.black);
            pageLayout.getAreaLayout().paint(g2,null,null,true);
                //paint the layout into the image
            return new ImageIcon(image);
        }
        Image fullImage = toolkit.createImage(filename);
        Image scaledImage = ImageBundle.createScaledImage(fullImage,
                0,ImageLister.ICON_SIZE,ImageLister.ICON_SIZE);
        return new ImageIcon(scaledImage);
    }
}