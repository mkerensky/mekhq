/*
 * SpacecraftCoolingSystem.java
 * 
 * Copyright (C) 2019, MegaMek team
 * 
 * This file is part of MekHQ.
 * 
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with MekHQ.  If not, see <http://www.gnu.org/licenses/>.
 */

package mekhq.campaign.parts;

import java.io.PrintWriter;

import mekhq.campaign.finances.Money;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import megamek.common.Aero;
import megamek.common.Entity;
import megamek.common.Jumpship;
import megamek.common.SmallCraft;
import megamek.common.TechAdvancement;
import megamek.common.verifier.TestAdvancedAerospace;
import megamek.common.verifier.TestSmallCraft;
import mekhq.MekHqXmlUtil;
import mekhq.campaign.Campaign;
import mekhq.campaign.personnel.SkillType;

/**
 * Container for SC/DS/JS/WS/SS heat sinks. Eliminates need for tracking hundreds/thousands
 * of individual heat sink parts for spacecraft.
 * 
 * The remove action adds a single heatsink of the appropriate type to the warehouse.
 * Fix action replaces one. 
 * Small craft and up don't actually track damage to heatsinks, so you only fix this part if you're salvaging/replacing.
 * There might be 12,500 heatsinks in here. Have fun with that.
 * @author MKerensky
 */
public class SpacecraftCoolingSystem extends Part {
    
    /**
     * 
     */
    private static final long serialVersionUID = -5530683467894875423L;
    
    private int sinkType;
    private int sinksNeeded;
    private int currentSinks;
    private int engineSinks;
    private int removeableSinks;
    private int totalSinks;
	
	public SpacecraftCoolingSystem() {
    	this(0, 0, 0, null);
    }
    
    public SpacecraftCoolingSystem(int tonnage, int totalSinks, int sinkType, Campaign c) {
        super(tonnage, c);
        this.name = "Spacecraft Cooling System";
        this.totalSinks = totalSinks;
        this.sinkType = sinkType;
        this.sinksNeeded = 0;
    }
        
    public SpacecraftCoolingSystem clone() {
    	SpacecraftCoolingSystem clone = new SpacecraftCoolingSystem(0, totalSinks, sinkType, campaign);
        clone.copyBaseData(this);
    	return clone;
    }
    
    //Getters for our various internal values
    public int getSinkType() {
        return sinkType;
    }
    
    public int getTotalSinks() {
        return totalSinks;
    }
    
	@Override
	public void updateConditionFromEntity(boolean checkForDestruction) {
	    if(null != unit && unit.getEntity() instanceof Aero) {
            totalSinks = ((Aero) unit.getEntity()).getOHeatSinks();
            currentSinks = ((Aero) unit.getEntity()).getHeatSinks();
            setEngineHeatSinks();
            removeableSinks = Math.max(0, (totalSinks - engineSinks));
            //You shouldn't be able to replace or remove heatsinks built into the vessel's engine
            sinksNeeded = Math.min(removeableSinks, (totalSinks - currentSinks));
        }
	}
	
	@Override 
	public int getBaseTime() {
	    if (isSalvaging()) {
            return 120;
        }
	    return 90;
	}
	
	@Override
	public int getDifficulty() {
	    if(isSalvaging()) {
            return -2;
        }
        return -1;
	}

	@Override
	public void updateConditionFromPart() {
		if(null != unit && unit.getEntity() instanceof Aero) {
			((Aero)unit.getEntity()).setHeatSinks(currentSinks);
		}
		
	}

	@Override
	public void fix() {
	    replaceHeatSink();
	}
	
	/**
	 * Pulls a heatsink of the appropriate type from the warehouse and adds it to the cooling system
	 * 
	 */
	public void replaceHeatSink() {
	    if (unit != null && unit.getEntity() instanceof Aero) {
	        //Spare part is usually 'this', but we're looking for spare heatsinks here...
	        Part spareHeatSink = new AeroHeatSink(0, ((Aero)unit.getEntity()).getHeatType(), false, campaign);
	        Part spare = campaign.checkForExistingSparePart(spareHeatSink);
	       if (null != spare) {
                spare.decrementQuantity();
                ((Aero)unit.getEntity()).setHeatSinks(((Aero)unit.getEntity()).getHeatSinks() + 1);
           }
	    }
	    updateConditionFromEntity(false);
	}
	
	/**
     * Calculates 'weight free' heatsinks included with this spacecraft's engine. You can't remove or replace these
     * 
     */
    public void setEngineHeatSinks() {
        //Only calculate this again if we've managed to keep a value of 0 engineSinks or go negative.
        //According to the construction rules, this *should* always be a positive value.
        if (engineSinks <= 0 && null != unit) {
            if (unit.getEntity() instanceof Jumpship) {
                engineSinks = TestAdvancedAerospace.weightFreeHeatSinks((Jumpship) unit.getEntity());
            } else if (unit.getEntity() instanceof SmallCraft) {
                engineSinks = TestSmallCraft.weightFreeHeatSinks((SmallCraft) unit.getEntity());
            }
        }
    }

	@Override
	public void remove(boolean salvage) {
		removeHeatSink(salvage);
	}
	
	/**
     * Pulls a heatsink of the appropriate type from the cooling system and adds it to the warehouse
     * 
     */
	public void removeHeatSink(boolean salvage) {
	    if (unit != null && unit.getEntity() instanceof Aero) {
            //Spare part is usually 'this', but we're looking for spare heatsinks here...
            Part spareHeatSink = new AeroHeatSink(0, ((Aero)unit.getEntity()).getHeatType(), false, campaign);
            Part spare = campaign.checkForExistingSparePart(spareHeatSink);
            if(!salvage) {
                //Scrapping. Shouldn't be able to get here, but don't do anything just in case.
            } else if (null != spare) {
                //Add one to our spare stocks
                spare.incrementQuantity();
                spare.setUnit(null);
           } else {
               //Start a new collection
               campaign.addPart(spareHeatSink, 0);
           }
           ((Aero)unit.getEntity()).setHeatSinks(((Aero)unit.getEntity()).getHeatSinks() - 1);
        }
	    updateConditionFromEntity(false);
	}

	@Override
	public MissingPart getMissingPart() {
	    //No missing part for this. Just heatsinks to go inside it.
		return null;
	}

	@Override
	public String checkFixable() {
		return null;
	}

	@Override
	public boolean needsFixing() {
		return sinksNeeded > 0;
	}

	@Override
	public Money getStickerPrice() {
	    //Cooling system itself has no price
		return Money.zero();
	}
	
	@Override
    public String checkScrappable() {
        return "Spacecraft Cooling System cannot be scrapped";
    }

    @Override
    public boolean canNeverScrap() {
        return true;
    }
	
	@Override
	public double getTonnage() {
		return 0;
	}

	@Override
	public boolean isSamePartType(Part part) {
	    //You don't ever replace or remove the whole cooling system, just modify it
		return false;
	}
	
	@Override
	public boolean isRightTechType(String skillType) {
	    return skillType.equals(SkillType.S_TECH_VESSEL);
	}
	
	@Override
	public void writeToXml(PrintWriter pw1, int indent) {
		writeToXmlBegin(pw1, indent);
		pw1.println(MekHqXmlUtil.indentStr(indent+1)
				+"<sinkType>"
				+sinkType
				+"</sinkType>");
		pw1.println(MekHqXmlUtil.indentStr(indent+1)
                +"<sinksNeeded>"
                +sinksNeeded
                +"</sinksNeeded>");
		pw1.println(MekHqXmlUtil.indentStr(indent+1)
                +"<currentSinks>"
                +currentSinks
                +"</currentSinks>");
		writeToXmlEnd(pw1, indent);
	}

	@Override
	protected void loadFieldsFromXmlNode(Node wn) {
		NodeList nl = wn.getChildNodes();
		
		for (int x=0; x<nl.getLength(); x++) {
			Node wn2 = nl.item(x);		
			if (wn2.getNodeName().equalsIgnoreCase("sinkType")) {
				sinkType = Integer.parseInt(wn2.getTextContent());
			} else if (wn2.getNodeName().equalsIgnoreCase("sinksNeeded")) {
			    sinksNeeded = Integer.parseInt(wn2.getTextContent());
            } else if (wn2.getNodeName().equalsIgnoreCase("currentSinks")) {
                currentSinks = Integer.parseInt(wn2.getTextContent());
            }
		}
	}

	@Override
	public String getLocationName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getLocation() {
		return Entity.LOC_NONE;
	}
	
	@Override
    public TechAdvancement getTechAdvancement() {
        if (sinkType == Aero.HEAT_SINGLE) {
            return AeroHeatSink.TA_SINGLE;
        } else {
            return AeroHeatSink.TA_IS_DOUBLE;
        }
    }
	
	
}