package com.attendra.uitm.model;

/** One room of /venues (seed data). GPS can't tell rooms apart, so it is a label only. */
public class Venue {
    public String id;
    public String name;
    /** "CS1" or "CS2" */
    public String building;
    public String floor;
    public String type;
    public int order;

    public Venue() {
    }
}
