package com.unihub.app.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;

/** Built-in course cards (course with its teachers) of this semester of this semester (2nd Year 2nd Term), from the printed sheets. */
public class SeedTeachers {
    private static List<TeacherInfo> directory() {
        List<TeacherInfo> t = new ArrayList<>();
        t.add(new TeacherInfo("MI", "Dr. Md. Milon Islam", "CSE", "milonislam@kuet.ac.bd", "017-18586-174"));
        t.add(new TeacherInfo("DD", "Mrs. Dola Das", "CSE", "dola.das@kuet.ac.bd", "01679-502532"));
        t.add(new TeacherInfo("SH", "Mr. Md. Sakhawat Hossain", "CSE", "sakhawat@kuet.ac.bd", "01863-651897"));
        t.add(new TeacherInfo("NS", "Mr. Md. Nazirulhasan Shawon", "CSE", "shawon@kuet.ac.bd", "01609-810194"));
        t.add(new TeacherInfo("SA", "Mr. Safin Ahmmed", "CSE", "safinahmmed@kuet.ac.bd", "01533-635763"));
        t.add(new TeacherInfo("SN", "Ms. Subah Nawar", "CSE", "nawar@kuet.ac.bd", "01825-556567"));
        t.add(new TeacherInfo("MAN", "Mr. Md. Mubtashim Abrar Nihal", "CSE", "", ""));
        t.add(new TeacherInfo("LBM", "Mrs. Lamisa Bintee Mizan Deya", "CSE", "", ""));
        t.add(new TeacherInfo("EK", "Mr. Ehsanul Karim", "CSE", "", ""));
        t.add(new TeacherInfo("FRH", "Dr. Md. Faruque Hossain", "ECE", "fhossain97@yahoo.com", "+8801714087376"));
        t.add(new TeacherInfo("FH", "Dr. Md. Faisal Hossain", "ECE", "", "+8801714087375"));
        t.add(new TeacherInfo("MRM", "Mr. Mushfiqur Rahman Masuk", "ECE", "", ""));
        t.add(new TeacherInfo("MIA", "Mr. Md. Minhajul Islam Arnab", "ECE", "", ""));
        t.add(new TeacherInfo("ARJ", "Dr. A. R. M. Jalal Uddin Jamali", "MATH", "jamali@math.kuet.ac.bd", "+8801714087243"));
        t.add(new TeacherInfo("HZ", "Dr. Md. Hasanuzzaman", "MATH", "hasanuzzaman@math.kuet.ac.bd", "+8801714838750"));
        t.add(new TeacherInfo("RA", "Dr. S.M Rabiul Alam", "HUM", "rabiul@hum.kuet.ac.bd", "+8801714087407"));
        t.add(new TeacherInfo("FA", "Ms. Farhana Afroj", "HUM", "farhana@hum.kuet.ac.bd", "+8801950153147"));
        return t;
    }

    private static CourseInfo card(String code, String title, String... shorts) {
        CourseInfo c = new CourseInfo();
        c.code = code; c.title = title;
        for (String sn : shorts) for (TeacherInfo t : directory()) if (t.shortName.equals(sn)) c.teachers.add(t);
        return c;
    }

    public static List<CourseInfo> courses() {
        List<CourseInfo> c = new ArrayList<>();
        c.add(card("CSE 2200", "Advanced Programming Laboratory", "SA", "EK"));
        c.add(card("CSE 2201", "Algorithm Analysis and Design", "SH", "MAN"));
        c.add(card("CSE 2202", "Algorithm Analysis and Design Laboratory", "SN", "MAN"));
        c.add(card("CSE 2208", "Numerical Methods Laboratory", "MI", "LBM"));
        c.add(card("CSE 2209", "Theory of Computation", "DD", "NS"));
        c.add(card("ECE 2213", "Digital Electronics", "FRH", "FH"));
        c.add(card("ECE 2214", "Digital Electronics Laboratory", "MRM", "MIA"));
        c.add(card("MATH 2207", "Complex Variable, Vector Analysis and Statistics", "ARJ", "HZ"));
        c.add(card("HUM 2207", "Economics and Accounting", "RA", "FA"));
        return c;
    }

    public static String courseId(String code) { return code.replace(" ", "").toUpperCase(); }

    /** Writes everything in one batch (fixed ids, so never duplicated). */
    public static Task<Void> upload() {
        FirebaseFirestore fs = FirebaseFirestore.getInstance();
        WriteBatch batch = fs.batch();
        for (CourseInfo c : courses()) batch.set(fs.collection("teacherCards").document(courseId(c.code)), c);
        return batch.commit();
    }
}
