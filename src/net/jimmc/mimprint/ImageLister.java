/* ImageLister.java
 *
 * Jim McBeath, September 18, 2001
 */

package jimmc.jiviewer;

import jimmc.util.FileUtil;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

/** Maintains a list of images and associated information.
 */
public class ImageLister extends JPanel implements ListSelectionListener {
	/** Our App. */
	protected App app;

	/** Our parent window. */
	protected Viewer viewer;

	/** The image area which displays the image. */
	protected ImageArea imageArea;

	/** Our list. */
	protected JList list;

	/** The label showing the directory info. */
	protected JTextArea dirInfoLabel;

	/** The label showing the file info. */
	protected JTextArea fileInfoLabel;

	/** The current directory in which we are displaying files. */
	protected File targetDirectory;

	/** The file names we are displaying, within the targetDirectory. */
	protected String[] fileNames;

	/** The currently displayed image. */
	protected ImageBundle currentImage;

	/** The next image in the list. */
	protected ImageBundle nextImage;

	/** The previous image in the list. */
	protected ImageBundle previousImage;

	/** Create a new list. */
	public ImageLister(App app, Viewer viewer) {
		super();
		this.app = app;
		this.viewer = viewer;

		dirInfoLabel = new JTextArea("dir info here");
		dirInfoLabel.setEditable(false);
		fileInfoLabel = new JTextArea("file info here");
		fileInfoLabel.setEditable(false);
		JSplitPane infoSplitPane = new JSplitPane(
			JSplitPane.VERTICAL_SPLIT,
			dirInfoLabel,fileInfoLabel);
		//infoSplitPane.setBackground(Color.black);

		list = new JList();
		list.addListSelectionListener(this);
		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setPreferredSize(new Dimension(600,100));

		JSplitPane splitPane = new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			listScrollPane,infoSplitPane);
		splitPane.setDividerLocation(200);

		setLayout(new BorderLayout());
		add(splitPane);

		initImageLoader();
	}

	/** Initialize our image loader thread. */
	protected void initImageLoader() {
		Thread imageLoader = new Thread() {
			public void run() {
				imageLoaderRun();
			}
		};
		imageLoader.setPriority(imageLoader.getPriority()-2);
		imageLoader.start();
	}

	/** Set the ImageArea. */
	public void setImageArea(ImageArea imageArea) {
		this.imageArea = imageArea;
	}

	/** Open the specified target.
	 * @param target The file or directory to open.
	 * @see #open(File)
	 */
	public void open(String target) {
		open(new File(target));
	}

	/** Open the specified target.
	 * If it is a directory, list all of the image files in the directory
	 * and select the first one.
	 * If it is a file, list all of the image files in the containing
	 * directory, and selected the given file.
	 * @param targetFile The file or directory to open.
	 */
	public void open(File targetFile) {
		if (!targetFile.exists()) {
			Object[] eArgs = { targetFile.getName() };
			String msg = app.getResourceFormatted(
					"error.NoSuchFile",eArgs);
			viewer.errorDialog(msg);
			return;
		}
		File previousTargetDirectory = targetDirectory;
		if (targetFile.isDirectory()) {
			//It's a directory, use it
			targetDirectory = targetFile;
			targetFile = null;	//get the real file later
		} else {
			//It's not a directory, get the directory from it
			targetDirectory = targetFile.getParentFile();
			if (targetDirectory==null) {
				//No parent, so the file must not name a
				//directory, so the directory must be "."
				targetDirectory = new File(".");
			}
		}
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return isImageFileName(name);
			}
		};
		currentImage = null;
		nextImage = null;
		previousImage = null;
		fileNames = targetDirectory.list(filter);
		Arrays.sort(fileNames,new ImageFileNameComparator());
		//TBD - look up file dates, sizes, and associated text
		if (previousTargetDirectory==null || targetDirectory==null ||
		    !previousTargetDirectory.toString().equals(
				targetDirectory.toString()))
			setDirectoryInfo(targetDirectory);
		list.setListData(fileNames);
		if (fileNames.length==0) {
			//No files in the list, so don't try to select anything
		} else if (targetFile==null) {
			//No file specified, so select the first file in the dir
			list.setSelectedIndex(0);
		} else {
			//Find the index of the specified file and select it
			String targetFileName = targetFile.getName();
			int n = Arrays.binarySearch(fileNames,targetFileName);
			if (n<0)
				n = 0;	//if file not found, select first file
			list.setSelectedIndex(n);
		}
		//move(0);	//go to the current image
	}

	/** Display new directory info. */
	protected void setDirectoryInfo(File dir) {
		String dirInfo = "Directory: "+dir.toString();
		try {
			File summaryFile = new File(dir,"summary.txt");
			String dirSummary = FileUtil.readFile(summaryFile);
			if (dirSummary!=null) {
				if (dirSummary.endsWith("\n")) {
					dirSummary = dirSummary.substring(0,
						dirSummary.length()-1);
				}
				dirInfo += "\nSummary: "+dirSummary;
			}
		} catch (Exception ex) {
			//on error, ignore summary
		}
		dirInfoLabel.setText(dirInfo);
	}

	/** Display new file info. */
	protected void setFileInfo(String path) {
		String fileInfo = "File: "+path;
		String fileText = getFileText(path);
		if (fileText!=null) {
			if (fileText.endsWith("\n")) {
				fileText = fileText.substring(
						0,fileText.length()-1);
			}
			fileInfo += "\n"+fileText;
		}
		fileInfoLabel.setText(fileInfo);
		//TBD - print "M of N" image files
		//TBD - print file size, date
	}

	/** Get the text for the specified file. */
	protected String getFileText(String path) {
		try {
			int dot = path.lastIndexOf('.');
			if (dot<0)
				return null;
			String textPath = path.substring(0,dot+1)+"txt";
			File f = new File(textPath);
			return FileUtil.readFile(f);
		} catch (Exception ex) {
			return null;	//on any error, ignore the file
		}
	}

	/** True if the file name is for an image file that we recognize. */
	public boolean isImageFileName(String name) {
		int dotPos = name.lastIndexOf('.');
		if (dotPos<0)
			return false;	//no extension
		String extension = name.substring(dotPos+1).toLowerCase();
		if (extension.equals("gif") ||
		    extension.equals("jpg") ||
		    extension.equals("jpeg"))
			return true;
		return false;
	}

	/** Here when the list selection changes. */
	public void valueChanged(ListSelectionEvent ev) {
		displayCurrentSelection();
	}

	/** Show the currently selected file. */
	public void displayCurrentSelection() {
		if (imageArea==null)
			return;
		setupCurrentImage();
		displayCurrentImage();
		setupNextImage();
	}

	/** Set up our images.
	 * @return The path to the current image.
	 */
	protected void setupCurrentImage() {
		int newSelection = list.getSelectedIndex();
		int currentSelection = (currentImage==null)?
					-1:currentImage.getListIndex();
		if (newSelection==currentSelection)
			return;		//no change, ignore this call

		if (newSelection<0) {
			//Nothing selected
			currentImage = null;
			nextImage = null;
			previousImage = null;
			return;
		}

		//Most common case: user is advancing through the images
		//one at a time.
		if (newSelection == currentSelection+1 && currentSelection>=0) {
			previousImage = currentImage;
			currentImage = nextImage;
			nextImage = null;
			return;
		}

		//Second common case: user is going backwards through the list.
		if (newSelection == currentSelection-1) {
			nextImage = currentImage;
			currentImage = previousImage;
			previousImage = null;
			return;
		}

		//Not an adjacent image
		nextImage = null;
		previousImage = null;

		File file = new File(targetDirectory,fileNames[newSelection]);
		if (file==null) {
			imageArea.showText("No file");
			currentImage = null;
			return;		//nothing there
		}
		currentImage = new ImageBundle(imageArea,file,newSelection);
	}

	/** Set up the next and previous images. */
	protected void setupNextImage() {
		int currentSelection = (currentImage==null)?
					-1:currentImage.getListIndex();
		int maxIndex = list.getModel().getSize();
		if (nextImage==null && currentSelection+1<maxIndex) {
			File file = new File(targetDirectory,
					fileNames[currentSelection+1]);
			if (file!=null) {
				nextImage = new ImageBundle(imageArea,
					file,currentSelection+1);
				synchronized(this) {
					notifyAll();	//start imageLoader
				}
			}
		}
		if (previousImage==null && currentSelection-1>=0) {
			File file = new File(targetDirectory,
					fileNames[currentSelection-1]);
			if (file!=null) {
				previousImage = new ImageBundle(imageArea,
					file, currentSelection-1);
				synchronized(this) {
					notifyAll();	//start imageLoader
				}
			}
		}
	}

	/** Display the current image. */
	protected void displayCurrentImage() {
		String path;
		if (currentImage==null) {
			path = null;
			imageArea.showText("No image");
			setFileInfo(null);
		} else {
			Image image = currentImage.getScaledImage();
			if (image==null)
				image = currentImage.getImage();
			imageArea.showImage(image);
			path = currentImage.getPath();
			setFileInfo(path);
		}
		viewer.setTitleFileName(path);
	}

	/** Move the selection up one item and show that file. */
	public void up() {
		move(-1);
	}

	/** Move the selection down one item and show that file. */
	public void down() {
		move(1);
	}

	/** Move the selection by the specified amount and show that file. */
	public void move(int inc) {
		int sel = list.getSelectedIndex();
		sel += inc;
		int maxIndex = list.getModel().getSize();
		if (sel<0 || sel>=maxIndex) {
			//TBD - put up dialog asking if we should move to
			//the next/previous directory
			//New selection value is out of range, ignore it
			getToolkit().beep();
			//TBD - put an error message in the status line in case
			//the beep is turned off.
			return;
		}
		list.setSelectedIndex(sel);
		list.ensureIndexIsVisible(sel);
		displayCurrentSelection();
	}

	/** The image loader thread, which loads images in the background. */
	public void imageLoaderRun() {
		while (true) {
			synchronized(this) {
				try {
					wait();
					Thread.sleep(100);
				} catch (InterruptedException ex) {
					//ignore
				}
			}
			if (nextImage!=null) {
				nextImage.loadScaledImage();
			}
			if (previousImage!=null) {
				previousImage.loadScaledImage();
			}
		}
	}
}

/* end */
