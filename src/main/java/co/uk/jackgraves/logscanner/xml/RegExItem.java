package co.uk.jackgraves.logscanner.xml;

import javax.xml.bind.annotation.*;

@SuppressWarnings("unused")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "RegexEntry")
public class RegExItem {
    @SuppressWarnings("unused")
    public String pageName;
    public String regex;
    public String URL;
    public String Id;
    public String sourceID;
}