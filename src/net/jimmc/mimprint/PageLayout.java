/* PageLayout.java
 *
 * Jim McBeath, November 3, 2005
 */

package jimmc.jiviewer;

import java.io.File;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Stack;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.SAXException;

/** The layout for a page of images.
 */
public class PageLayout {
    private static final int BORDER_THICKNESS = 20;

    private String description; //description of this layout

    private int pageUnit;    //name of our units, e.g. "in", "cm"
        public final static int UNIT_CM = 0;    //metric
        public final static int UNIT_INCH = 1;  //english
        public final static int UNIT_MULTIPLIER = 1000;
    //The actual values we store in our various dimension fields
    //are the units times the multiplier.  For example, we
    //would represent 8.5 inches as 8500.
    private int pageWidth;      //width of the page in pageUnits
    private int pageHeight;     //height of the page in pageUnits

    private AreaLayout areaLayout;

    private AreaLayout currentArea;     //when loading an XML file

    /** Create a PageLayout. */
    public PageLayout() {
        //nothing here
    }

    public void setDefaultLayout() {
        pageUnit = UNIT_INCH;
        pageWidth = 8500;       //American standard paper size
        pageHeight = 11000;

        int margin = 500;       //margin on outer edges
        int spacing = 250;      //spacing between areas
        int rowCount = 2;        //default number of rows
        int columnCount = 2;

        //areaLayout = new AreaGridLayout();
        areaLayout = new ImagePageArea(0,0,0,0);

        setAreaLayoutBounds();
        areaLayout.setMargin(margin);
        areaLayout.setSpacing(spacing);
        areaLayout.setBorderThickness(BORDER_THICKNESS);
        if (areaLayout instanceof AreaGridLayout)
            ((AreaGridLayout)areaLayout).setRowColumnCounts(rowCount,columnCount);
        areaLayout.revalidate();        //set up areas

        //For testing, throw in a Split
        //AreaSplitLayout splitArea = new AreaSplitLayout();
        //splitArea.setBorderThickness(BORDER_THICKNESS);
        //areaLayout.areas[3] = splitArea;        //hack
        //areaLayout.revalidate();        //make sure Split gets set up properly
    }

    private void setAreaLayoutBounds() {
        if (areaLayout==null)
            return;
        areaLayout.setBounds(0,0,pageWidth,pageHeight);
    }

    /** Set the descriptive text for this layout. */
    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Set the page units.
     * @param unit One of UNIT_CM or UNIT_INCH.
     */
    public void setPageUnit(int unit) {
        if (unit==pageUnit)
            return;     //no change
        switch (unit) {
        case UNIT_CM:
        case UNIT_INCH:
            pageUnit = unit;
            break;
        default:
            throw new IllegalArgumentException("bad units "+unit);
        }
    }

    /** Get the current page units, either UNIT_CM or UNIT_INCH. */
    public int getPageUnit() {
        return pageUnit;
    }

    public void setPageWidth(int width) {
        this.pageWidth = width;
        if (areaLayout==null)
            return;
        setAreaLayoutBounds();
        areaLayout.revalidate();
    }

    public int getPageWidth() {
        return pageWidth;
    }

    public void setPageHeight(int height) {
        this.pageHeight = height;
        if (areaLayout==null)
            return;
        setAreaLayoutBounds();
        areaLayout.revalidate();
    }

    public int getPageHeight() {
        return pageHeight;
    }

    protected void setAreaLayout(AreaLayout areaLayout) {
        this.areaLayout = areaLayout;
        setAreaLayoutBounds();
        areaLayout.setBorderThickness(BORDER_THICKNESS);
        areaLayout.revalidate();
    }

    protected AreaLayout getAreaLayout() {
        return areaLayout;
    }

    /** Write the current layout template. */
    protected void writeLayoutTemplate(PrintWriter pw) {
        pw.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");  //XML header line
        //TODO - write out DTD line?
        String pageLineFmt = "<page width=\"{0}\" height=\"{1}\" unit=\"{2}\">";
        Object[] pageLineArgs = {
            formatPageValue(getPageWidth()),
            formatPageValue(getPageHeight()),
            (getPageUnit()==PageLayout.UNIT_CM)?"cm":"in"
        };
        pw.println(MessageFormat.format(pageLineFmt,pageLineArgs));
        if (description!=null)
            pw.println("    <description>"+description+"</description>");
        areaLayout.writeTemplate(pw,1);
        pw.println("</page>");
    }

    /** Read in the specified layout template. */
    protected void loadLayoutTemplate(File f) {
        SAXParser parser;
        try {
            parser = SAXParserFactory.newInstance().newSAXParser();
                //TODO - create factory only once
        } catch (Exception ex) {
            throw new RuntimeException("Exception creating SAXParser",ex);
        }
System.out.println("Created SAXParser");
        DefaultHandler handler = new PageLayoutHandler();
        try {
            parser.parse(f,handler);
        } catch (Exception ex) { //SAXException, IOException
            throw new RuntimeException("Error parsing xml",ex);
        }
    }

    private static void initPageValueFormat() {
        if (pageValueFormat==null) {
            pageValueFormat = NumberFormat.getNumberInstance();
            pageValueFormat.setMaximumFractionDigits(3);
        }
    }
    private static NumberFormat pageValueFormat;

    protected static String formatPageValue(int n) {
        initPageValueFormat();
        double d = ((double)n)/UNIT_MULTIPLIER;
        return pageValueFormat.format(new Double(d));
    }

    protected static int parsePageValue(String s) {
        double d = Double.parseDouble(s);
        return (int)(d*UNIT_MULTIPLIER);
    }

    class PageLayoutHandler extends DefaultHandler {
        private Stack areaStack;
        private String lastText;        //most recent parsed text

        public void startDocument() {
System.out.println("startDocument");
            areaStack  = new Stack();
        }

        public void endDocument() {
System.out.println("endDocument");
        }

        public void startElement(String uri, String localName,
                String qName, Attributes attributes) {
System.out.println("startElement "+uri+","+localName+","+
                    qName+",attrs="+attributes);
            if (qName.equals("description"))
                ;       //ignore the start, pick up the text on the end
            else if (qName.equals("page"))
                loadPageAttributes(attributes);
            else {
                AreaLayout newArea = AreaLayoutFactory.newAreaLayout(qName);
                newArea.setBorderThickness(BORDER_THICKNESS);
                newArea.setXmlAttributes(attributes);
                //TODO - ensure that newArea.areas has been allocated
                areaStack.push(currentArea);
                currentArea = newArea;
            }
        }

        private void loadPageAttributes(Attributes attrs) {
            String heightStr = attrs.getValue("height");
            String widthStr = attrs.getValue("width");
            if (heightStr==null || widthStr==null ||
                    heightStr.trim().equals("") || widthStr.trim().equals("")) {
                throw new IllegalArgumentException(
                        "page height and width must be specified"); //TODO i18n
            }
            setPageWidth(parsePageValue(widthStr));
            setPageHeight(parsePageValue(heightStr));
            String unitStr = attrs.getValue("unit");
            if ("cm".equalsIgnoreCase(unitStr))
                setPageUnit(UNIT_CM);
            else if ("in".equalsIgnoreCase(unitStr))
                setPageUnit(UNIT_INCH);
            else
                throw new IllegalArgumentException(
                        "page unit must be \"cm\" or \"in\""); //TODO i18n
        }

        public void characters(char[] ch, int start, int end) {
            lastText = new String(ch,start,end);
        }

        public void endElement(String uri, String localName,
                String qName) {
System.out.println("endElement "+uri+","+localName+","+
                    qName);
            //TODO - validate end element matches start element
            if (qName.equals("description")) {
                if (lastText!=null)
                    setDescription(lastText);
                return;
            } else if (qName.equals("page")) {
                if (currentArea!=null)
                    throw new RuntimeException("Expected area end element before </page>"); //TODO i18n
                return;         //done with the page
            } else {
                ;       //nothing here
            }
            AreaLayout newArea = currentArea;
            currentArea = (AreaLayout)areaStack.pop();
            if (currentArea==null)
                setAreaLayout(newArea); //set page layout
            else
                currentArea.addAreaLayout(newArea);
        }
    }
}