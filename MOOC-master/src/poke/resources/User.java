package poke.resources;

/**
 * Created by spurthy on 12/2/15.
 */
public class User {

    String fname;
    String lname;
    String email;
    String pwd;

    public User(String fname, String lname, String email, String pwd) {
        this.fname = fname;
        this.lname = lname;
        this.email = email;
        this.pwd = pwd;
    }

    public String getFname() {
        return fname;
    }

    public void setFname(String fname) {
        this.fname = fname;
    }

    public String getLname() {
        return lname;
    }

    public void setLname(String lname) {
        this.lname = lname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }
}
