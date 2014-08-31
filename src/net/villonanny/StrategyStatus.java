package net.villonanny;
//*****fofo created class. 
// Stores Strategy done status so they are available between villages
// Stores resource requests so they are available between villages
//

import org.apache.log4j.Logger;

import net.villonanny.strategy.RequestResources;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;

public class StrategyStatus {
	static Element myListStart = null;
	boolean debuglog=false;
	private final static Logger log = Logger.getLogger(StrategyStatus.class);	
	class Element {
		Element previous;
		Element next;
		String strategyID;
		Boolean done;
		ResourceTypeMap requestedResources;
		String[] coords;
		
		public Element () {
			previous=null;
			next=null;
			strategyID="";
			done = false;
			requestedResources=null;
			coords=null;
		}

		public Element ( Element before, Element after, String id, boolean Done) {
			previous=before;
			next=after;
			strategyID=id;
			done=Done;
			requestedResources=null;
			coords=null;
		}  //constructor end
		
	} // element end
	
	public void clearAllRequestedResources () {
		Element current;
		
		log.debug("Clearing all remaining resource requests");
		current =myListStart;
		while (current != null) {
			if (current.requestedResources!=null)
				log.debug("("+current.requestedResources.toStringNoFood()+") still needed by "+current.strategyID);
			current.requestedResources=null;
			current=current.next;
		}
	} 

	public void updateRequestedResources (String strategyID, ResourceTypeMap sentResources) {
		Element current;  //sentResources should contain the resources that is already sent, so we subtract that from the request
		Boolean FoundIt=false;
		boolean doneFlag=false;
		int requested, sent, stillneeded, zerocount;
		if (debuglog) log.debug("updating req. ress. sent is"+sentResources.toStringNoFood());
		if (myListStart!=null) {
			current=myListStart;
			FoundIt=false;
			while ((current != null) &&(FoundIt==false)){
				if (current.strategyID.equals(strategyID)){
					FoundIt=true;
					if (debuglog) log.debug("Original req. amount was   "+current.requestedResources.toStringNoFood());
					zerocount=0;
					for (ResourceType aresource : sentResources.keySet()) {
						if (aresource == ResourceType.FOOD) {
							continue;
						}
						requested=current.requestedResources.get(aresource);
						sent=sentResources.get(aresource);
						stillneeded=requested-sent;
						if (stillneeded<=0){
							zerocount=zerocount+1;
							stillneeded=0;
						}						
						current.requestedResources.put(aresource, stillneeded);
					}
					if (debuglog) log.debug("Remaining request is       "+current.requestedResources.toStringNoFood());
					if (zerocount==4) {
						current.requestedResources=null;
						log.debug("Request from "+strategyID+" was served fully");
					}
					else
						log.debug("Resources ("+current.requestedResources.toStringNoFood()+") still needed by "+strategyID);
					if (debuglog) log.debug("Strategy status."+strategyID+". was here already and now set to ."+Boolean.toString(doneFlag));
				}
				else
				{
					current=current.next;
				}
			} //while
		} //if != null
		if (FoundIt==false)
			log.trace("No request was made from "+strategyID+" error");
	} 
	
	public String[] getRequestingCoords (String StrategyID) {
		Element current;
		
		current =myListStart;
		while (current != null) {
			if (debuglog) log.debug("Strategy status. looking for ."+StrategyID+". found ."+current.strategyID);
			if (current.strategyID.equals(StrategyID)) {
				if (debuglog) {
					log.debug("Strategy status."+StrategyID+".found");
					log.debug("Requested coords: ("+current.coords[0]+"|"+current.coords[1]+")");
				}
				return current.coords;
			}
			current=current.next;
		}
		if (debuglog) log.debug("Coords for."+StrategyID+".not found, returning false");
		return null; //return null if it has not registered as true
	} 

	public ResourceTypeMap getRequestedResources (String StrategyID) {
		Element current;
		ResourceTypeMap returnMap = new ResourceTypeMap();
		current =myListStart;
		while (current != null) {
			if (debuglog) log.debug("Strategy status. looking for ."+StrategyID+". found ."+current.strategyID);
			if ((current.strategyID.equals(StrategyID)) && (current.requestedResources!=null)) {
				if (debuglog) {
					log.debug("Strategy status."+StrategyID+".found");
					log.debug("Requested resources: "+current.requestedResources.toStringNoFood());
				}
				for (ResourceType aresource : returnMap.keySet()) {
					if (aresource == ResourceType.FOOD) {
						continue;
					}
					returnMap.put(aresource, current.requestedResources.get(aresource));
				}
				return returnMap;  //safeguard against current.requestedResources values getting changed by external routines
			} else {
				if ((current.strategyID.equals(StrategyID)) && (current.requestedResources==null)) {
					return null;
				}
			}
			current=current.next;
		}
		if (debuglog) log.debug("Resourses for."+StrategyID+".not found, returning false");
		return null; //return null if it has not registered as true
	} //GetTimeToRun

	public void setRequestedResources (String StrategyID, ResourceTypeMap requestedResources, String[] coords) {
		Element current, tail;
		Boolean FoundIt;
		boolean DoneFlag=false;
		
		if (debuglog) log.debug("Strategy status. setting ."+StrategyID+". and set to ."+Boolean.toString(DoneFlag));
		if (myListStart==null)
		{
			myListStart=new Element (null, null, StrategyID, DoneFlag);
			myListStart.requestedResources=requestedResources;
			myListStart.coords=coords;
			if (debuglog) log.debug("Strategy status. first entry."+StrategyID+". and its ."+Boolean.toString(DoneFlag));
		}
		else //try to find the strategy in the list
		{
			current=myListStart;
			tail=myListStart;
			FoundIt=false;
			while ((current != null) &&(FoundIt==false)){
				if (current.strategyID.equals(StrategyID)){
					if (debuglog) log.debug("Strategy status."+StrategyID+". was here already and now set to ."+Boolean.toString(DoneFlag));
					FoundIt=true;
				}
				else
				{
					tail=current;
					current=current.next;
				}
			} //while
			if (current == null) { //didn't exist before, so create a new
				current = new Element (tail, null, StrategyID, DoneFlag);
				tail.next=current;
				current.previous=tail;
			}
			current.done=DoneFlag;
			current.requestedResources=requestedResources;
			current.coords=coords;
			if (debuglog) log.debug("Strategy status."+StrategyID+". not first, but new. now set to ."+Boolean.toString(DoneFlag));
		} 
			
	} 
	
	public void setFinished (String StrategyID, boolean DoneFlag) {
		Element current, tail;
		Boolean FoundIt;
		
		if (debuglog) log.debug("Strategy status. setting ."+StrategyID+". and set to ."+Boolean.toString(DoneFlag));
		if (myListStart==null)
		{
			myListStart=new Element (null, null, StrategyID, DoneFlag);
			if (debuglog) log.debug("Strategy status. first entry."+StrategyID+". and its ."+Boolean.toString(DoneFlag));
		}
		else //try to find the strategy in the list
		{
			current=myListStart;
			tail=myListStart;
			FoundIt=false;
			while ((current != null) &&(FoundIt==false)){
				if (current.strategyID.equals(StrategyID)){
					current.done=DoneFlag;
					if (debuglog) log.debug("Strategy status."+StrategyID+". was here already ("+DoneFlag+") and now set to ."+Boolean.toString(DoneFlag));
					FoundIt=true;
				}
				else
				{
					tail=current;
					current=current.next;
				}
			} //while
			if (current == null) {
				current = new Element (tail, null, StrategyID, DoneFlag);
				tail.next=current;
				current.previous=tail;
				if (debuglog) log.debug("Strategy status."+StrategyID+". not first, but new. now set to ."+Boolean.toString(DoneFlag));
			}
		} //else
			
	} //SetTimeToRun
	
	public boolean getFinished (String StrategyID) {
		Element current;
		
		current =myListStart;
		while (current != null) {
			if (debuglog) log.debug("Strategy status. looking for ."+StrategyID+". found ."+current.strategyID);
			if (current.strategyID.equals(StrategyID)) {
				if (current.done == true){
					if (debuglog) log.debug("found."+current.strategyID+".and its done");
				}
				else {
					if (debuglog) log.debug("found."+current.strategyID+".and its not done");
				}
				if (debuglog) log.debug("Strategy status."+StrategyID+".found and its."+Boolean.toString(current.done));
				return current.done;
			}
			current=current.next;
		}
		if (debuglog) log.debug("Strategy status."+StrategyID+".not found, returning false");
		return false; //return false if it has not registered as true
	} //GetTimeToRun

public void removeStragegy (String ToRemove) {
	Element current, tail;
	Boolean FoundIt;
	
	current=myListStart;
	tail=myListStart;
	FoundIt=false;
	while ((current != null) &&(FoundIt==false)){
		if (current.strategyID.equals(ToRemove)){
			FoundIt=true;
		}
		else
		{
			tail=current;
			current=current.next;
		}
	} //while
	if (FoundIt == true) {
		tail.next = current.next;
		if (current.next != null) {
			current=current.next;
			current.previous=tail;
		}
	}
} //RemoveStrategy


}