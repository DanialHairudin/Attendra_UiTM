package com.attendra.uitm.model;

/**
 * One row of /groups (seed data). The id is the node key, e.g. "CS2405A"
 * = programme CS240, part 5, letter A.
 */
public class Group {
    /** Node key. Not stored inside the node, so we fill it in after reading. */
    public String id;
    public String programme;
    public int part;
    public String letter;

    public Group() {
    }
}
