/* Viewer.java
 *
 * Jim McBeath, September 15, 2001
 */

package jimmc.jiviewer;

import jimmc.swing.GridBagger;
import jimmc.swing.JsFrame;
import jimmc.swing.MenuAction;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.text.MessageFormat;
import java.util.Vector;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JWindow;

/** The top window class for the jiviewer program.
 */
public class Viewer extends JsFrame {
	/** Our application info. */
	private App app;

	/** The list of images. */
	private ImageLister imageLister;

	/** Our image display area. */
	private ImageArea imageArea;
	private ImageArea fullImageArea;

        /** Full-screen window. */
        private JFrame fullWindow;
            //Have to use a Frame here rather than a window; when using
            //a window, the keyboard input is directed to the main
            //frame (Viewer), which causes problems.
        /** Alternate screen window. */
        private JWindow altWindow;
        private JFrame imagePageWindow;

	/** The current screen mode. */
	private int screenMode;

	/** The screen bounds when in normal mode. */
	private Rectangle normalBounds;

	/** The latest file opened with the File Open dialog. */
	private File currentOpenFile;

	/** Create our frame. */
	public Viewer(App app) {
		super();
		setResourceSource(app);
		this.app = app;
		setJMenuBar(createMenuBar());
		initForm();
		pack();
                imageArea.requestFocus();
		addWindowListener();
		setTitleFileName("");
	}

        protected Component getDialogParent() {
            if (fullWindow!=null)
                return fullWindow;
            else
                return this;
        }

	/** Our message display text area uses a big font so we can read it
	 * on the TV. */
	protected JTextArea getMessageDisplayTextArea(String msg) {
		JTextArea textArea = super.getMessageDisplayTextArea(msg);
		if (app.useBigFont()) {
			Font bigFont = new Font("Serif",Font.PLAIN,25);
			textArea.setFont(bigFont);
			maxSimpleMessageLength = 0;	//use special dialogs
		}
		return textArea;
	}

	/** Create our menu bar. */
	protected JMenuBar createMenuBar() {
		JMenuBar mb = new JMenuBar();
		mb.add(createFileMenu());
		mb.add(createHelpMenu());
		return mb;
	}

	/** Create our File menu. */
	protected JMenu createFileMenu() {
		JMenu m = new JMenu("File");
		MenuAction mi;

		String openLabel = getResourceString("menu.File.Open.label");
		mi = new MenuAction(openLabel) {
			public void action() {
				processFileOpen();
			}
		};
		m.add(mi);

		String exitLabel = getResourceString("menu.File.Exit.label");
		mi = new MenuAction(exitLabel) {
			public void action() {
				processFileExit();
			}
		};
		m.add(mi);

		return m;
	}

	/** Create the body of our form. */
	protected void initForm() {
		imageLister = new ImageLister(app,this);
		imageArea = new ImageArea(app,this);
		imageLister.setImageArea(imageArea);
		JSplitPane splitPane = new JSplitPane(
			JSplitPane.VERTICAL_SPLIT,imageLister,imageArea);
		splitPane.setBackground(imageArea.getBackground());
		getContentPane().add(splitPane);
	}

	/** Get our App. */
	public App getApp() {
		return app;
	}

	/** Open the specified target. */
	public void open(String target) {
		currentOpenFile = new File(target);
		imageLister.open(target);
	}

	/** Open the specified target. */
	public void open(File targetFile) {
		currentOpenFile = targetFile;
		imageLister.open(targetFile);
	}

	/** Set our status info. */
	public void setStatus(String status) {
		imageLister.setStatus(status);
	}

	/** Process the File->Open menu command. */
	protected void processFileOpen() {
		String msg = getResourceString("query.FileToOpen");
		String dflt = null;
		if (currentOpenFile!=null)
			dflt = currentOpenFile.getAbsolutePath();
		File newOpenFile = fileOpenDialog(msg,dflt);
		if (newOpenFile==null)
			return;		//nothing specified
		currentOpenFile = newOpenFile;
		open(currentOpenFile);		//open it
	}

	/** Closing this form exits the program. */
	protected void processClose() {
		processFileExit();
	}

	//Just exit, don't bother asking
	protected boolean confirmExit() {
		return true;
	}

	/** Set the specified file name into our title line. */
	public void setTitleFileName(String fileName) {
		if (fileName==null)
			fileName = "";
		String title;
		if (fileName.equals(""))
			title = getResourceString("title.nofile");
		else {
			Object[] args = { fileName };
			String fmt = getResourceString("title.fileName");
			title = MessageFormat.format(fmt,args);
		}
		setTitle(title);
	}

        public final static int SCREEN_NORMAL = 0;
        public final static int SCREEN_FULL = 1;
        public final static int SCREEN_PRINT = 2;
        public final static int SCREEN_ALT = 3;
	/** Set the image area to take up the full screen, or unset.
	 * @param fullImage true to make the ImageArea take up the full
	 *        screen, false to go back to the non-full-screen.
	 */
	public void setScreenMode(int mode) {
		if (mode==screenMode)
			return;		//already in that mode
                //imageLister.setVisible(!fullImage);
                    //Calling setVisible gets rid of the imageLister when we
                    //switch to full screen mode, but it doesn't come back
                    //when we return to normal mode.
                if (mode!=SCREEN_FULL) {
                    this.show();
                }
                switch (mode) {
                case SCREEN_NORMAL:
                    {
                    //Switch back to normal bounds
                    //setBounds(normalBounds.x, normalBounds.y,
                    //	normalBounds.width, normalBounds.height);
                    imageLister.setImageArea(imageArea);
                    fullImageArea = null;
                    imageArea.requestFocus();
                    }
                    break;
                case SCREEN_FULL:
                    {
                    Dimension screenSize = getToolkit().getScreenSize();
                    fullImageArea = new ImageArea(app,this);
                    fullWindow = new JFrame();
                    fullWindow.getContentPane().add(fullImageArea);
                    fullWindow.setBounds(0,0,screenSize.width,screenSize.height);
                    fullWindow.setBackground(fullImageArea.getBackground());
                    imageLister.setImageArea(fullImageArea);
                    fullWindow.show();
                    fullImageArea.requestFocus();
                    this.hide();
                    imageArea.showText("see full page window for image");
                    }
                    break;
                case SCREEN_ALT:
                    {
                    GraphicsEnvironment ge = GraphicsEnvironment.
                            getLocalGraphicsEnvironment();
                    GraphicsDevice[] gs = ge.getScreenDevices();
                    Vector configs = new Vector();
                    for (int i=0; i<gs.length; i++) {
                        GraphicsDevice gd = gs[i];
                        GraphicsConfiguration[] gc = gd.getConfigurations();
                        for (int j=0; j<gc.length; j++) {
                            configs.addElement(gc[j]);
                        }
                    }
                    if (configs.size()<2) {
                        getToolkit().beep();
                        return;         //only one display, no mode change
                    }
                    //TODO - if more than 2 configs, ask which one to use
                    //For now, just use the second config

                    GraphicsConfiguration gc = 
                            (GraphicsConfiguration)configs.elementAt(1);
                    Rectangle altScreenBounds = gc.getBounds();

                    fullImageArea = new ImageArea(app,this);
                    altWindow = new JWindow();
                    altWindow.getContentPane().add(fullImageArea);
                    altWindow.setBounds(altScreenBounds);
                    altWindow.setBackground(fullImageArea.getBackground());
                    imageLister.setImageArea(fullImageArea);
                    altWindow.show();
                    fullImageArea.requestFocus();
                    imageArea.showText("see alternate screen for image");
                    }
                    break;
                case SCREEN_PRINT:
                    {
                    ImagePage imagePage = new ImagePage();
                    imagePage.setBackground(Color.gray);
                    imagePage.setForeground(Color.black);
                    imagePage.setPageColor(Color.white);
                    imagePageWindow = new JFrame();
                    imagePageWindow.getContentPane().add(imagePage);
                    imagePageWindow.setBounds(300,0,300,500);
                    imageLister.setImagePage(imagePage);
                    imagePageWindow.show();
                    imagePage.requestFocus();
                    imageArea.showText("see image page window for image");
                    }
                    break;
		}
                if (mode!=SCREEN_FULL && fullWindow!=null) {
                    fullWindow.hide();
                    fullWindow.dispose();
                    fullWindow = null;
                }
                if (mode!=SCREEN_ALT && altWindow!=null) {
                    altWindow.hide();
                    altWindow.dispose();
                    altWindow = null;
                }
                if (mode!=SCREEN_PRINT && imagePageWindow!=null) {
                    imagePageWindow.hide();
                    imagePageWindow.dispose();
                    imagePageWindow = null;
                }
		screenMode = mode;
                imageLister.displayCurrentSelection();
                try {
                    Thread.sleep(200);  //give image updater a bit of time to run
                } catch (InterruptedException ex) {
                    //ignore
                }
                if (altWindow!=null)
                    altWindow.validate();
                if (fullWindow!=null)
                    fullWindow.validate();
                else
                    this.validate();
	}

	/** Display the specified text, allow the user to edit it.
	 * @param title The title of the editing dialog.
	 * @param text The text to display and edit.
	 * @return The edited string, or null if cancelled.
	 */
	protected String editTextDialog(String title, String text) {
		JTextArea tx = new JTextArea(text);
		JScrollPane scroll = new JScrollPane(tx);
		scroll.setPreferredSize(new Dimension(500,200));
		JOptionPane pane =
			new JOptionPane(scroll,
				JOptionPane.PLAIN_MESSAGE,
				JOptionPane.OK_CANCEL_OPTION);
		JDialog dlg = pane.createDialog(getDialogParent(),title);
		dlg.setResizable(true);
		pane.setInitialValue(null);
		pane.selectInitialValue();
		dlg.show();	//get user's changes

		Object v = pane.getValue();
		if (!(v instanceof Integer))
			return null;		//CLOSED_OPTION
		int n = ((Integer)v).intValue();
		if (n==JOptionPane.NO_OPTION || n==JOptionPane.CANCEL_OPTION)
			return null;		//canceled
		String newText = tx.getText();
		return newText;
	}

	/** Put up an editing dialog showing the image info. */
	public void showImageEditDialog() {
            String imageText = imageLister.getCurrentImageFileText();
            if (imageText==null)
                    imageText = "";
            String title = "Info text for "+imageLister.currentImage.path;
                            //TBD i18n and better title
            String newImageText = editTextDialog(title,imageText);
            if (newImageText==null)
                    return;		//cancelled
            imageLister.setCurrentImageFileText(newImageText);
	}

        /** Rotate the current image. */
        public void rotateCurrentImage(int quarters) {
            if (fullImageArea!=null)
                fullImageArea.rotate(quarters);
            else
                imageArea.rotate(quarters);
        }

        /** Move the active image up to the previous image in the lister. */
        public void moveUp() {
            imageLister.up();
        }

        /** Move the active image down to the next image in the lister. */
        public void moveDown() {
            imageLister.down();
        }

        /** Move the active image left to the previous dir in the lister. */
        public void moveLeft() {
            imageLister.left();
        }

        /** Move the active image right to the next dir in the lister. */
        public void moveRight() {
            imageLister.right();
        }

	/** Get a string from our resources. */
	public String getResourceString(String name) {
		return app.getResourceString(name);
	}
}

/* end */
