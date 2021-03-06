package publishingscripts.orcid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

public class ORCIDProfile {
	
	//first argument is orcid
	  public static void main(String[] args){
		  try{
			  for(int k=0; k<args.length; k++){
			  ORCIDProfile profile  = new ORCIDProfile(args[k]);
			  profile.extractEntries();
			 profile.printEntries();
			  }
		  }catch(Exception exc){
			  exc.printStackTrace();
		  }
	  }
	
	
	 final JSONObject profile;
	 final JSONArray works;
	final  String orcid, given_name, family_name;
	 final Set<String> worktypes = new HashSet<String>();
	 final BibTexEntry entries[];
	
	public ORCIDProfile(String orcid) throws Exception{
		
		String st;
	     File infile = new File(orcid+".json");
	     if(false && infile.exists()){
	    	 //for some reason reading from a rile doesnt work at the moment
	    	 BufferedReader br = new BufferedReader(new FileReader(infile));
	    	 st = br.readLine().replaceAll("\n", "");
	    	 br.close();
	     }else{
	    	 st =  getResponse(orcid);
	    	 
	    	 this.orcid = orcid;
		
	    	 Thread.currentThread().sleep(1000);
	    	 PrintWriter pw = new PrintWriter(new FileWriter(infile));
	    	 pw.print(st);
	    	 pw.close();
	     }
		 this.profile= new JSONObject(st);
		JSONObject details = (JSONObject)  get(this.profile, "orcid-profile:orcid-bio:personal-details");
		given_name = (String) get(details, "given-names:value");
		 family_name = (String) get(details, "family-name:value");   
		 works = (JSONArray)  get(this.profile, "orcid-profile:orcid-activities:orcid-works:orcid-work");
		 System.err.println("found "+works.length());
		 entries = new BibTexEntry[works.length()];
		
	}
	
	public void printEntries(){
		for(Iterator<String>it = this.worktypes.iterator(); it.hasNext();){
			try{
				String wt = it.next();
			PrintWriter pw = new PrintWriter(new FileWriter(new File(orcid+"."+wt+".bib")));
			for(int i=0; i<entries.length; i++){
				if(entries[i].worktype.equals(wt)) entries[i].write(pw);
			}
			pw.close();
			}catch(Exception exc){
				exc.printStackTrace();
			}
		}
	}
	
	Set<String> codes = new HashSet<String>();
	
	class BibTexEntry{
		public String title, journal, year, pp, vol,  no, doi, publisher;
		private String code, citation, worktype, bibtex;
		List<String> authors = new ArrayList<String>();
		
		String getAuthStr(){
			StringBuffer authstr = new StringBuffer();
			for(int i=0 ;i<authors.size(); i++){
				List<String>str = new ArrayList<String> (Arrays.asList(authors.get(i).split("\\s+")));
				
				String authn;
				
					authn = str.remove(str.size()-1)+", ";
				
				for(int j=0; j<str.size();j++ ){
					
					String authnj = str.get(j);
					
					if(authnj.toUpperCase().equals(authnj)){
						char[] c = authnj.toCharArray();
						char[] c1 = new char[c.length*3];
						for(int k=0; k<c.length; k++){
							c1[k*3] = c[k]; c1[k*3+1] = '.';
							c1[k*3+2] = ' ';
						}
						authnj = new String(c1);
					}
					authn = authn+authnj;
				}
				authstr.append((i>0 ? " and ": "")+ authn);
			}
			return authstr.toString();
		}
		
		public void write(PrintWriter pw) throws Exception {
			if(this.bibtex!=null){
				pw.println(bibtex);
			}else{
			pw.println("@article{"+code+",");
			pw.println(" author = {"+getAuthStr()+ "} ");
			Field[] fields = BibTexEntry.class.getFields();
			for(int k=0; k<fields.length; k++){
				Field f = fields[k];
				Object val = f.get(this);
				if(val instanceof String){
					String nme = f.getName();
					pw.println(" "+translate(nme)+ " = {"+ val+"}");
				}
			}
			pw.println("}");
			}
			// TODO Auto-generated method stub
		}

		private String translate(String nme) {
			if(nme=="pp") return "pages";
			if(nme=="vol") return "volume";
			if(nme=="no") return "number";
			else return nme;
		}

		String extract(String cit1, String start, String end){
			int pp = cit1.indexOf(start);
			if(pp>=0){
				return  cit1.substring(pp, cit1.indexOf(end, pp));
			}
			return null;
		}
		
		
		/* This attempts to extract information from a free form citation.
		 * It is not guaranteed to work for all formats, so use with caution
		 *  */ 
		void extractInfoFromCitation(int lastIndex) {
			try{
//				System.err.println(citation);
			    if( citation ==null || lastIndex <0 || lastIndex>=citation.length()) return;
				String cit1 = citation.substring(lastIndex);
				List<String> all = new ArrayList<String>(Arrays.asList(cit1.split(",")));
				List<String> type = new ArrayList<String>(Arrays.asList("vol:pp:no".split(":")));
				for(int k=0; k<type.size(); k++){
					for(int j=0; j<all.size(); j++){
						if(all.get(j).indexOf(type.get(k)+".")>=0){
							String val = all.remove(j).trim().split("\\s+")[1].trim();
							if(BibTexEntry.class.getField(type.get(k)).get(this)==null){
								BibTexEntry.class.getField(type.get(k)).set(this, val);
							}
						}
					}
				}
				
				for(int j=0; j<all.size(); j++){
					String val = all.get(j).trim();
					if(val.length()==4){
						if(year==null) year = val;
						all.remove(j);
					}
					int brack = val.indexOf('(');
					if(brack>0){
						int closeb =  val.indexOf(')', brack);
						if(publisher==null && closeb >0) publisher = val.substring(brack+1,closeb).trim();
						if(journal == null) journal = val.substring(0, brack).trim();
						all.remove(j);
					}
				}
				if(journal==null && all.size()>0){
					if(journal==null) journal = all.get(0).trim();
				}
			}catch(Exception exc){
				exc.printStackTrace();
			}
		}
		
		BibTexEntry(JSONObject work){
			title = (String) get(work, "work-title:title:value");
			journal= (String) get(work, "journal-title:title:value");
			citation= (String) get(work, "work-citation:citation");
			String citationtype =  (String) get(work, "work-citation:work-citation-type");// citationtype;
			worktype = (String) get(work,"work-type");
			worktypes.add(worktype);
			year = (String) get(work,"publication-date:year:value");
			JSONArray idents = (JSONArray) get(work,"work-external-identifiers:work-external-identifier");
			JSONObject dois = idents==null ? null : select(idents,"work-external-identifier-type", "DOI" );
			doi = (String) get(dois,"work-external-identifier-id:value");
			JSONArray auths = (JSONArray) get(work,"work-contributors:contributor");
			int lastIndex = 0;
			if(auths==null){
				System.err.println("auths is null");
			}else{
			int author_index = -1; // which index is author
			boolean lastfirst = false; // if format is LastName, FirstName
			for(int i=0 ;i<auths.length(); i++){
				JSONObject auth = (JSONObject) auths.get(i);
				if(auth==null) continue;
				String au = ((String) get(auth,"credit-name:value"));
				//System.err.println(au);
				
				if(au!=null){
					authors.add(au);
					String[] names  = au.split("\\s+");
					if(names[names.length-1].toLowerCase().equals(family_name.toLowerCase()) &&  given_name.toLowerCase().startsWith(names[0].toLowerCase().charAt(0)+"")){
							author_index = i;
					}else if(names[0].toLowerCase().equals(family_name.toLowerCase()) && given_name.toLowerCase().startsWith(names[1].toLowerCase().charAt(0)+"")){
						author_index = i;
						lastfirst = true;
				    }
					if(names[names.length-1].toUpperCase().equals(names[names.length-1])) {
						lastfirst = true;
					}
					if(citation!=null){
						int li = citation.lastIndexOf(au);
						if(li<0) li = citation.lastIndexOf(names[names.length-1]);
						if(li > lastIndex) lastIndex = li+ au.length()+1;
					}
					
				}
			}
			System.err.println(authors);
			if(lastfirst){
				for(int i=0; i<authors.size(); i++){
					String[] names = authors.get(i).split("\\s+");
					String auth = "";
					for(int k=1; k<names.length; k++){
						auth = auth + names[k]+" ";
					}
					auth = auth + names[0];
					authors.set(i, auth);
				}
			}
			System.err.println(authors);
		//	System.err.println("author_index "+author_index);
			/*for(int i=0 ;i<auths.length(); i++){
				JSONObject auth = (JSONObject) auths.get(i);
				if(auth==null) System.err.println("problem with "+title);
				if(auth==null) continue;
				String au = ((String) get(auth,"credit-name:value"));
				if(au!=null){
					au = au.trim();
					
				
					String[] names  = au.split("\\s+");
					
				}
			}*/
			}
			if(citation.indexOf("@article")==0 || citationtype.toUpperCase().equals("BIBTEX")){
				this.bibtex = citation;
			}else{
				 extractInfoFromCitation( lastIndex);
			}
				 code = "unknown"+year;
				if(authors.size()>0){
				String auth1 = authors.get(0).trim();
				String[] names = auth1.split("\\s+");
				 code = names[0]+year;
				}
				int index = 1;
				while(codes.contains(code)){
					code = code+"_"+index;
					index =index+1;
				}
				
				
	}
	
	}
	
	/** gets BibtexEntry for ith work */
	public void extractEntries() throws Exception{
		for(int i=0; i<works.length(); i++){
			JSONObject work = (JSONObject) works.get(i);
			this.entries[i] = new BibTexEntry(work);
		}
	 }
	
private static JSONObject select(JSONArray idents, String string, String string2) {
	for(int i =0; i<idents.length(); i++){
		JSONObject obj = (JSONObject) idents.get(i);
		if(obj.get(string).equals(string2))return obj;
	}
	return null;
}


  
	  private static Object get(JSONObject obj, String p){
		  try{
		  String[] path = p.split(":");
		  JSONObject obj1 = obj;
		  for(int i=0; i<path.length; i++){
			  if(i<path.length-1) obj1 = obj1.getJSONObject(path[i]);
			  else{
				  Object res =  obj1.get(path[i]);
					  if(res !=null && res instanceof String) res = ((String)res).trim();
					  return res;
			  }
		  }
		  return obj1;
		  }catch(Exception exc){
			  System.err.println(exc.getMessage());
		  }
		  return null;
	  }
  
  
	  /** This pulls the orcid record back via publilc API */
	public static String getResponse(String orcid) throws Exception{
		URL url = new URL("http://pub.orcid.org/"+orcid+"/orcid-profile");
	String output = "";	
	try{	 
		    URLConnection connection = url.openConnection();
		    HttpURLConnection httpConnection = (HttpURLConnection)connection;
		    httpConnection.setFollowRedirects(true);
		    httpConnection.setRequestProperty("Content-Type", "text/plain");
		    httpConnection.setRequestProperty("Accept", "application/orcid+json");
		    InputStream response = connection.getInputStream();
		    int responseCode = httpConnection.getResponseCode();
		    if(responseCode ==202) return null;
		    if(responseCode != 200) {
		      throw new RuntimeException("Response code was not 200. Detected response was "+responseCode);
		    }
		    Reader reader = null;
		      reader = new BufferedReader(new InputStreamReader(response, "UTF-8"));
		      StringBuilder builder = new StringBuilder();
		      char[] buffer = new char[8192];
		      int read;
		      while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
		        builder.append(buffer, 0, read);
		      }
		      output = builder.toString();
		      reader.close();
		 
		   
		   
	}catch(Exception exc){
		exc.printStackTrace();
	}
	return output;
	/* following attempt to remove odd UTF characters 
	Pattern p = Pattern.compile("\\\\u(\\p{XDigit}{4})");
	Matcher m = p.matcher(output);
	StringBuffer buf = new StringBuffer(output.length());
	while (m.find()) {
	  String ch = String.valueOf((char) Integer.parseInt(m.group(1), 16));
	  m.appendReplacement(buf, Matcher.quoteReplacement(ch));
	}
	m.appendTail(buf);
	return buf.toString();*/

	}
}
