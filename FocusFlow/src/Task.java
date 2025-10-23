import java.io.Serializable;

public class Task implements Serializable {
    private String title;
    private boolean done;

    public Task(String title, boolean done) {
        this.title = title;
        this.done = done;
    }

    public Task(String csv) {
        String[] p = csv.split("\\|", -1);
        this.title = p.length>0?p[0]:"";
        this.done = p.length>1?Boolean.parseBoolean(p[1]):false;
    }

    public String toCSV() {
        return escape(title) + "|" + done;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("|", "/PIPE/").replace("\n", "/NL/");
    }

    private String unescape(String s) {
        if (s == null) return "";
        return s.replace("/PIPE/", "|").replace("/NL/", "\n");
    }

    public String getTitle() { return title; }
    public boolean isDone() { return done; }
    public void setTitle(String t) { title = t; }
    public void setDone(boolean d) { done = d; }

    @Override
    public String toString() {
        return (done ? "[✓] " : "[ ] ") + title;
    }
}