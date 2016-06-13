
package lupapalvelu.tiedonohjaus;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MetadataType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MetadataType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}TransferInformation"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}ContactInformation"/>
 *         &lt;element ref="{http://www.arkisto.fi/skeemat/Sahke2/2011/12/20}CaseFile" maxOccurs="unbounded"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MetadataType", propOrder = {
    "transferInformation",
    "contactInformation",
    "caseFile"
})
public class MetadataType {

    @XmlElement(name = "TransferInformation", required = true)
    protected TransferInformationType transferInformation;
    @XmlElement(name = "ContactInformation", required = true)
    protected ContactInformationType contactInformation;
    @XmlElement(name = "CaseFile", required = true)
    protected List<CaseFile> caseFile;

    /**
     * Gets the value of the transferInformation property.
     * 
     * @return
     *     possible object is
     *     {@link TransferInformationType }
     *     
     */
    public TransferInformationType getTransferInformation() {
        return transferInformation;
    }

    /**
     * Sets the value of the transferInformation property.
     * 
     * @param value
     *     allowed object is
     *     {@link TransferInformationType }
     *     
     */
    public void setTransferInformation(TransferInformationType value) {
        this.transferInformation = value;
    }

    /**
     * Gets the value of the contactInformation property.
     * 
     * @return
     *     possible object is
     *     {@link ContactInformationType }
     *     
     */
    public ContactInformationType getContactInformation() {
        return contactInformation;
    }

    /**
     * Sets the value of the contactInformation property.
     * 
     * @param value
     *     allowed object is
     *     {@link ContactInformationType }
     *     
     */
    public void setContactInformation(ContactInformationType value) {
        this.contactInformation = value;
    }

    /**
     * Gets the value of the caseFile property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the caseFile property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCaseFile().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link CaseFile }
     * 
     * 
     */
    public List<CaseFile> getCaseFile() {
        if (caseFile == null) {
            caseFile = new ArrayList<CaseFile>();
        }
        return this.caseFile;
    }

}
