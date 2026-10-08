package com.miniplm;

import com.miniplm.model.BomLink;
import com.miniplm.model.LifecycleState;
import com.miniplm.model.Part;
import com.miniplm.model.PartVersion;
import com.miniplm.model.Role;
import com.miniplm.model.User;

/** Small helpers that build in-memory objects for the tests (no database needed). */
public final class TestData {

    private TestData() {
    }

    public static Part part(long id, String partNumber) {
        Part part = new Part();
        part.setId(id);
        part.setPartNumber(partNumber);
        part.setName("Part " + partNumber);
        return part;
    }

    public static PartVersion version(long id, Part part, String revision, LifecycleState state) {
        PartVersion version = new PartVersion();
        version.setId(id);
        version.setPart(part);
        version.setRevision(revision);
        version.setState(state);
        return version;
    }

    public static User user(long id, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setName(email);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    public static BomLink link(long id, PartVersion parent, Part child, int quantity) {
        BomLink link = new BomLink();
        link.setId(id);
        link.setParentVersion(parent);
        link.setChildPart(child);
        link.setQuantity(quantity);
        link.setUnit("EA");
        return link;
    }
}
