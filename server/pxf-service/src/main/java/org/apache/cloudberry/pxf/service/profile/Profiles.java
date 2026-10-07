package org.apache.cloudberry.pxf.service.profile;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * Profiles is the root element for the list of profiles
 * defined in the profiles XML file
 */
@XmlRootElement(name = "profiles")
@XmlAccessorType(XmlAccessType.FIELD)
public class Profiles {

    @XmlElement(name = "profile")
    private List<Profile> profiles;

    /**
     * Returns a list of {@link Profile} objects
     * @return a list of {@link Profile} objects
     */
    public List<Profile> getProfiles() {
        return profiles;
    }
}
