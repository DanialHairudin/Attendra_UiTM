package com.attendra.uitm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the Register form collects, already validated.
 * Student-only and lecturer-only fields are left null for the other role.
 */
public class Registration {
    public static final String ROLE_STUDENT = "student";
    public static final String ROLE_LECTURER = "lecturer";

    public String role;
    public String name;
    public String email;
    /** Full number with country code, e.g. "+60123456789". */
    public String phone;
    public String password;

    // Student only
    public String studentId;
    public String programme;
    public int part;
    public String groupId;

    // Lecturer only
    public String staffId;

    /** Clubs the student joins, or the clubs the lecturer advises. */
    public List<String> clubIds = new ArrayList<>();

    public boolean isStudent() {
        return ROLE_STUDENT.equals(role);
    }
}
