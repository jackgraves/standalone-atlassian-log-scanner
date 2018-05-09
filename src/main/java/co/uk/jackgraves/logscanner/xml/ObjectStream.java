package co.uk.jackgraves.logscanner.xml;

import javax.xml.bind.annotation.*;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "object-stream")
public class ObjectStream {
    @XmlElement(name = "RegexEntry")
    public List<RegExItem> regexItems;
}
