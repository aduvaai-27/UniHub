package com.unihub.app.data;

public class CourseStat {
    public String name;
    public int present, total;
    public int percent() { return AttendanceLevel.percent(present, total); }
    public int absent() { return total - present; }
}
