package gtandroid.universal;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Хранилище ADM-форм и настройка фильтра по командам. */
public final class AdminFormManager {
    private static final String PREFS = "GTAndroidData";
    private static final String KEY_PENDING = "admin_forms_pending";
    private static final String KEY_ENABLED = "admin_form_enabled_commands";
    private static final String[] INITIALS = {"/offjail", "/jail", "/offmute", "/mute", "/offwarn", "/warn", "/offban", "/ban", "/rmute", "/kick", "/sban"};
    private static final String[] NO_INITIALS = {"/unjail", "/offunjail", "/unwarn", "/offunwarn", "/unban", "/unmute", "/runmute", "/skick", "/uncuff", "/slap", "/spawn", "/spcar", "/spcars", "/setskin", "/fixcar", "/flip", "/freeze", "/hp", "/arm", "/setfuel", "/rgun", "/unfreeze"};
    private static final Pattern FORM = Pattern.compile("(?im)<\\s*ADM\\s*>\\s*\\(([^)]*)\\)\\s+\\(?([^\\s\\[)]+)\\)?\\[(\\d{1,8})\\]\\s*:\\s*([^\\r\\n]+)");
    private AdminFormManager() {}
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public static String[] getAllCommands() { String[] a = new String[INITIALS.length + NO_INITIALS.length]; System.arraycopy(INITIALS,0,a,0,INITIALS.length); System.arraycopy(NO_INITIALS,0,a,INITIALS.length,NO_INITIALS.length); return a; }
    public static boolean requiresInitials(String command) { String c=firstCommand(command); for(String s:INITIALS) if(s.equals(c)) return true; return false; }
    public static synchronized Set<String> getEnabledCommands(Context c) {
        if (!prefs(c).contains(KEY_ENABLED)) { LinkedHashSet<String> d=new LinkedHashSet<>(); for(String s:INITIALS)d.add(s); return d; }
        HashSet<String> out=new HashSet<>(); for(String s:prefs(c).getString(KEY_ENABLED,"").split(",")){s=normalize(s);if(!s.isEmpty())out.add(s);} return out;
    }
    public static synchronized void setEnabledCommands(Context c, Set<String> commands) { StringBuilder b=new StringBuilder(); for(String s:commands){s=normalize(s);if(!s.isEmpty()){if(b.length()>0)b.append(',');b.append(s);}} prefs(c).edit().putString(KEY_ENABLED,b.toString()).apply(); }
    public static boolean isCommandEnabled(Context c,String command){return getEnabledCommands(c).contains(firstCommand(command));}
    public static synchronized ArrayList<Form> getPending(Context c){ArrayList<Form> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs(c).getString(KEY_PENDING,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(Form.fromJson(o));}}catch(Exception ignored){}return out;}
    public static synchronized boolean addForm(Context c,Form f){if(f==null||!isCommandEnabled(c,f.command))return false;ArrayList<Form>a=getPending(c);for(Form old:a)if(old.uniqueKey().equals(f.uniqueKey()))return false;a.add(0,f);while(a.size()>100)a.remove(a.size()-1);save(c,a);return true;}
    public static synchronized void removeForm(Context c,String key){ArrayList<Form>a=getPending(c);for(int i=a.size()-1;i>=0;i--)if(a.get(i).uniqueKey().equals(key))a.remove(i);save(c,a);}
    public static synchronized void clearForms(Context c){prefs(c).edit().remove(KEY_PENDING).apply();}
    private static void save(Context c,ArrayList<Form> forms){JSONArray a=new JSONArray();for(Form f:forms)try{a.put(f.toJson());}catch(Exception ignored){}prefs(c).edit().putString(KEY_PENDING,a.toString()).apply();}
    public static ArrayList<Form> parseForms(String text){ArrayList<Form> out=new ArrayList<>();if(text==null)return out;Matcher m=FORM.matcher(text.replace("< ADM>","<ADM>"));while(m.find()){String command=m.group(4).trim();if(!command.startsWith("/"))continue;String first=firstCommand(command);boolean known=false;for(String s:getAllCommands())if(s.equals(first)){known=true;break;}if(known)out.add(new Form(m.group(1).trim(),m.group(2).trim(),m.group(3).trim(),command,System.currentTimeMillis()));}return out;}
    public static String makeAcceptedCommand(Context c,Form f){String raw=f.command==null?"":f.command.trim();if(!requiresInitials(raw))return raw;Matcher target=Pattern.compile("^\\(([^)]+)\\)\\s*(/.*)$").matcher(raw);if(target.matches())return target.group(2).trim()+" | "+makeInitials(target.group(1).trim());if(raw.contains(" | "))return raw;String initials=makeInitials(f.nickname);return initials.isEmpty()?raw:raw+" | "+initials;}
    public static String firstCommand(String command){if(command==null)return "";String v=command.trim();if(v.startsWith("(")){int i=v.indexOf(')');if(i>=0&&i+1<v.length())v=v.substring(i+1).trim();}int s=v.indexOf(' ');return normalize(s>=0?v.substring(0,s):v);}
    private static String normalize(String s){if(s==null)return "";s=s.trim().toLowerCase(Locale.ROOT);if(!s.isEmpty()&&!s.startsWith("/"))s="/"+s;return s;}
    public static String makeInitials(String nickname){if(nickname==null)return "";String v=nickname.trim();int i=v.indexOf('_');if(i>0&&i<v.length()-1)return v.substring(0,1).toUpperCase(Locale.ROOT)+"."+v.substring(i+1);return v;}
    public static final class Form { public final String rank,nickname,id,command;public final long createdAt;public Form(String r,String n,String i,String c,long t){rank=r;nickname=n;id=i;command=c;createdAt=t;}public String uniqueKey(){return rank+"|"+nickname+"|"+id+"|"+command;}public JSONObject toJson()throws Exception{JSONObject o=new JSONObject();o.put("rank",rank);o.put("nickname",nickname);o.put("id",id);o.put("command",command);o.put("createdAt",createdAt);return o;}public static Form fromJson(JSONObject o){return new Form(o.optString("rank",""),o.optString("nickname",""),o.optString("id",""),o.optString("command",""),o.optLong("createdAt",System.currentTimeMillis()));}}
}
