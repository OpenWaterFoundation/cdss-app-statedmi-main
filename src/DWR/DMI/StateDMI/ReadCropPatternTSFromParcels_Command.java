// ReadCropPatternTSFromParcels_Command - This class initializes, checks, and runs the ReadCropPatternTSFromHydroBase() command.

/* NoticeStart

StateDMI
StateDMI is a part of Colorado's Decision Support Systems (CDSS)
Copyright (C) 1997-2019 Colorado Department of Natural Resources

StateDMI is free software:  you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

StateDMI is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

You should have received a copy of the GNU General Public License
    along with StateDMI.  If not, see <https://www.gnu.org/licenses/>.

NoticeEnd */

package DWR.DMI.StateDMI;

import javax.swing.JFrame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import DWR.DMI.HydroBaseDMI.HydroBaseDMI;
import DWR.StateCU.IncludeParcelInCdsType;
import DWR.StateCU.StateCU_CropPatternTS;
import DWR.StateCU.StateCU_Location;
import DWR.StateCU.StateCU_Parcel;
import DWR.StateCU.StateCU_Supply;
import DWR.StateCU.StateCU_SupplyFromGW;
import DWR.StateCU.StateCU_SupplyFromSW;
import DWR.StateCU.StateCU_Util;
import RTi.Util.Message.Message;
import RTi.Util.Message.MessageUtil;
import RTi.TS.YearTS;
import RTi.Util.IO.AbstractCommand;
import RTi.Util.IO.Command;
import RTi.Util.IO.CommandException;
import RTi.Util.IO.CommandLogRecord;
import RTi.Util.IO.CommandPhaseType;
import RTi.Util.IO.CommandStatus;
import RTi.Util.IO.CommandStatusType;
import RTi.Util.IO.CommandWarningException;
import RTi.Util.IO.InvalidCommandParameterException;
import RTi.Util.IO.PropList;
import RTi.Util.String.StringUtil;
import RTi.Util.Time.DateTime;

/**
This class initializes, checks, and runs the ReadCropPatternTSFromParcels() command.
*/
public class ReadCropPatternTSFromParcels_Command 
extends AbstractCommand implements Command
{
	
/**
Constructor.
*/
public ReadCropPatternTSFromParcels_Command ()
{	super();
	setCommandName ( "ReadCropPatternTSFromParcels" );
}

/**
 * Add a parcel's area to the crop pattern area.
 * This is an internal method.
 * @param debug whether in debug mode, for troubleshooting
 * @param culoc the CU location being processed.
 * @param parcel the parcel being processed.
 * @param yts the crop pattern time series to add to.
 * @param temp_DateTime the DateTime at which to add the value, reused for optimization.
 * @param parcelYear the year for data (will be set in temp_DateTime).
 * @param parcelYears list of all years that have been processed, will update for the parcelYear.
 * @param parcelCrop the crop for the parcel
 * @param areaIrrig the area irrigated by a supply.
 */
private void addParcelArea ( boolean debug, StateCU_Location culoc, StateCU_Parcel parcel, YearTS yts, DateTime temp_DateTime,
	int parcelYear, List<Integer> parcelYears, String parcelCrop, double areaIrrig ) {
	double val = yts.getDataValue ( temp_DateTime );
	int dl = 1;
	if ( yts.isDataMissing(val) ) {
		// Value is missing so set...
		if ( debug ) {
			Message.printDebug ( dl, "", "  Initializing " + culoc.getID() + " from parcelId=" + parcel.getID() + " " +
			parcelYear + " " + parcelCrop + " to " + StringUtil.formatString(areaIrrig,"%.3f") );
		}
		yts.setDataValue ( temp_DateTime, areaIrrig );
	}
	else {
		// Value is not missing.  Need to either set or add to it...
		if ( debug ) {
			Message.printDebug ( dl, "", "  Adding " + culoc.getID() + " from parcelId=" + parcel.getID() + " " +
				parcelYear + " " + parcelCrop + " + " + areaIrrig + " = " +
				StringUtil.formatString( (val + areaIrrig), "%.3f") );
		}
		yts.setDataValue ( temp_DateTime, val + areaIrrig );
	}
	addToParcelYears ( parcelYear, parcelYears );
}	

/**
Add parcel data from HydroBase to a StateCU_CropPatternTS so that it can be
used later for filling and data checks.  For example, the FillCropPatternTSUsingWellRights() command
uses the data.  This method DOES NOT manage the crop pattern time series - it simply adds the parcel
data related to the crop pattern time series to a list.
@param cds StateCU_CropPatternTS instance to in which to store data.
@param parcel_id The identifier for the parcel.
@param year The year for the parcel.
@param land_use The land use (crop name) for the parcel.
@param area The area of the parcel.
@param units The area units for the parcel.
*/
/* TODO smalers 2020-10-11 experimental - leave out for now to keep things simpler
private void addParcelToCropPatternTS ( StateCU_CropPatternTS cds, String parcel_id, int year,
	String land_use, double area, String units )
{
	StateCU_Parcel parcel = new StateCU_Parcel();
	parcel.setID ( parcel_id );
	parcel.setYear ( year );
	parcel.setArea ( area );
	parcel.setAreaUnits ( units );
	cds.addParcel ( parcel );
}
*/

/**
Add to the unique list of parcel years that were processed.
@param parcelYear parcel year being processed
@param parcelYears list of parcel years being processed.
*/
private void addToParcelYears ( int parcelYear, List<Integer> parcelYears )
{	boolean found = false;
	for ( Integer parcelYear0 : parcelYears ) {
		if ( parcelYear0.equals(parcelYear) ) {
			found = true;
			break;
		}
	}
	if ( !found ) {
		// Add a new parcel year
		parcelYears.add ( new Integer(parcelYear) );
	}
}

/**
Check the command parameter for valid values, combination, etc.
@param parameters The parameters for the command.
@param command_tag an indicator to be used when printing messages, to allow a
cross-reference to the original commands.
@param warning_level The warning level to use when printing parse warnings
(recommended is 2 for initialization, and 1 for interactive command editor dialogs).
*/
public void checkCommandParameters ( PropList parameters, String command_tag, int warning_level )
throws InvalidCommandParameterException
{	String routine = getClass().getName() + ".checkCommandParameters";
	String ID = parameters.getValue ( "ID" );
	String InputStart = parameters.getValue ( "InputStart" );
	String InputEnd = parameters.getValue( "InputEnd" );
	String message;
	String warning = "";
	
	CommandStatus status = getCommandStatus();
	status.clearLog(CommandPhaseType.INITIALIZATION);
	
	if ( (ID == null) || (ID.length() == 0) ) {
		message = "An identifier or pattern must be specified.";
		warning += "\n" + message;
		status.addToLog ( CommandPhaseType.INITIALIZATION,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Specify the identifier pattern to match." ) );
	}
	
	if ( (InputStart != null) && (InputStart.length() != 0) && !StringUtil.isInteger(InputStart) ) {
		message = "The input start is not a valid year.";
		warning += "\n" + message;
		status.addToLog ( CommandPhaseType.INITIALIZATION,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Specify the input start as an integer YYYY." ) );
	}
	
	if ( (InputEnd != null) && (InputEnd.length() != 0) && !StringUtil.isInteger(InputEnd) ) {
		message = "The input end is not a valid year.";
		warning += "\n" + message;
		status.addToLog ( CommandPhaseType.INITIALIZATION,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Specify the input end as an integer YYYY." ) );
	}
	
	// Check for invalid parameters...
	List<String> validList = new ArrayList<>(3);
    validList.add ( "ID" );
    validList.add ( "InputStart" );
    validList.add ( "InputEnd" );
	warning = StateDMICommandProcessorUtil.validateParameterNames ( validList, this, warning );

	if ( warning.length() > 0 ) {
		Message.printWarning ( warning_level,
		MessageUtil.formatMessageTag(command_tag,warning_level), routine, warning );
		throw new InvalidCommandParameterException ( warning );
	}
	status.refreshPhaseSeverity(CommandPhaseType.INITIALIZATION,CommandStatusType.SUCCESS);
}

/**
Edit the command.
@return true if the command was edited (e.g., "OK" was pressed), and false if
not (e.g., "Cancel" was pressed.
*/
public boolean editCommand ( JFrame parent )
{	// The command will be modified if changed...
	return (new ReadCropPatternTSFromParcels_JDialog ( parent, this )).ok();
}

/**
Reset crop pattern time series to zero, used in cases where multiple
readCropPatternTSFromHydroBase() commands are used.
@param cdsList list of StateCU_CropPatternTS being processed.
@param culoc_id Identifier for CU location to have its crop pattern time series reset.
@param cal_year_start The first calendar year to reset.
@param cal_year_end The last calendar year to reset.
*/
/* TODO smalers 2020-10-11 not needed for simpler logic of this new command?
private void resetCropPatternTS ( StateDMI_Processor processor, List<StateCU_CropPatternTS> cdsList,
	DateTime OutputStart_DateTime, DateTime OutputEnd_DateTime,
	String culoc_id, int cal_year_start, int cal_year_end )
{
	// Get the crop pattern time series for the location.  If none
	// matches, return without changing anything (data will be added OK).
	StateCU_CropPatternTS cds = null;
	int pos = StateCU_Util.indexOf(cdsList,culoc_id);
	if ( pos >= 0 ) {
		// Get the time series...
		cds = (StateCU_CropPatternTS)cdsList.get(pos);
	}
	if ( cds == null ) {
		// No need to reset...
		return;
	}
	List<String> crop_names = cds.getCropNames();
	int ncrop_names = 0;
	if ( crop_names != null ) {
		ncrop_names = crop_names.size();
	}
	int year = 0;
	String units = cds.getUnits();
	if ( units == null ) {
		units = "ACRE";
	}
	for ( int ic = 0; ic < ncrop_names; ic++ ) {
		for ( year = cal_year_start; year <= cal_year_end; year++ ){
			// Replace or add in the list.  Pass individual fields because we may or
			// may not need to add a new StateCU_CropPatternTS or a time series in the object...
			processor.findAndAddCUCropPatternTSValue (
				culoc_id, culoc_id,
				year,
				-1,
				(String)crop_names.get(ic),
				0.0,
				OutputStart_DateTime,
				OutputEnd_DateTime,
				units, 0 );
		}
	}
}
*/

/**
Method to execute the ReadCropPatternTSFromParcels() command.
@param command_number Command number in sequence.
@exception Exception if there is an error processing the command.
*/
public void runCommand ( int command_number )
throws InvalidCommandParameterException, CommandWarningException, CommandException
{	String message, routine = getCommandName() + "_Command.runCommand";
	// Use to allow called methods to increment warning count.
	int warningLevel = 2;
	int log_level = 3;
	String command_tag = "" + command_number;
	int warning_count = 0;
	//int dl = 1; // Debug level
	
	CommandPhaseType commandPhase = CommandPhaseType.RUN;
	StateDMI_Processor processor = (StateDMI_Processor)getCommandProcessor();
	CommandStatus status = getCommandStatus();
	status.clearLog(CommandPhaseType.RUN);
	
	// Get the input parameters...
	
	PropList parameters = getCommandParameters();
	String ID = parameters.getValue ( "ID" );
	if ( ID == null ) {
		ID = "*"; // Default
	}
	String idpattern_Java = StringUtil.replaceString(ID,"*",".*");
	String InputStart = parameters.getValue( "InputStart" );
	int InputStart_int = -1;
	if ( InputStart != null ) {
		InputStart_int = Integer.parseInt(InputStart);
	}
	String InputEnd = parameters.getValue( "InputEnd" );
	int InputEnd_int = -1;
	if ( InputEnd != null ) {
		InputEnd_int = Integer.parseInt(InputEnd);
	}

	// Get the list of CU locations.
	
	List<StateCU_Location> culocList = null;
	try {
		@SuppressWarnings("unchecked")
		List<StateCU_Location> dataList = (List<StateCU_Location>)processor.getPropContents ( "StateCU_Location_List");
		culocList = dataList;
	}
	catch ( Exception e ) {
		message = "Error requesting CU location data from processor.";
		Message.printWarning(warningLevel,
			MessageUtil.formatMessageTag( command_tag, ++warning_count), routine, message );
		status.addToLog ( CommandPhaseType.RUN,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Report problem to software support." ) );
	}

	// Get the list of crop pattern time series.
	// - this would have been initialized with CreateCropPatternTSForCULocations
	
	List<StateCU_CropPatternTS> cdsList = null;
	int cdsListSize = 0;
	try {
		@SuppressWarnings("unchecked")
		List<StateCU_CropPatternTS> dataList = (List<StateCU_CropPatternTS>)processor.getPropContents ( "StateCU_CropPatternTS_List");
		cdsList = dataList;
		cdsListSize = cdsList.size();
	}
	catch ( Exception e ) {
		message = "Error requesting crop pattern time series data from processor.";
		Message.printWarning(warningLevel,
			MessageUtil.formatMessageTag( command_tag, ++warning_count),
			routine, message );
		status.addToLog ( CommandPhaseType.RUN,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Report problem to software support." ) );
	}
	if ( cdsListSize == 0 ) {
		message = "No crop pattern time series are defined.";
		Message.printWarning(warningLevel,
			MessageUtil.formatMessageTag( command_tag, ++warning_count),
			routine, message );
		status.addToLog ( CommandPhaseType.RUN,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Run CreateCropPatternTSForCULocations() before this command." ) );
	}

	/*
	Get the map of parcel data for newer StateDMI.
	*/
	HashMap<String,StateCU_Parcel> parcelMap = null;
	try {
		@SuppressWarnings("unchecked")
		HashMap<String,StateCU_Parcel> dataMap =
			(HashMap<String,StateCU_Parcel>)processor.getPropContents ( "StateCU_Parcel_List");
		parcelMap = dataMap;
	}
	catch ( Exception e ) {
		message = "Error requesting parcel data from processor.";
		Message.printWarning(warningLevel,
			MessageUtil.formatMessageTag( command_tag, ++warning_count),
			routine, message );
		status.addToLog ( CommandPhaseType.RUN,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Report problem to software support." ) );
	}
	if ( parcelMap == null ) {
		message = "Parcel list (map) is null.";
		Message.printWarning ( warningLevel, 
			MessageUtil.formatMessageTag(command_tag, ++warning_count), routine, message );
		status.addToLog ( commandPhase,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Software logic problem - results will not be correct.") );
	}
	
    // Output period will be used if not specified with InputStart and InputEnd
    
    DateTime OutputStart_DateTime = null;
    try {
    	OutputStart_DateTime = (DateTime)processor.getPropContents ( "OutputStart");
    }
    catch ( Exception e ) {
        Message.printWarning ( log_level, routine, e );
        message = "Error requesting OutputStart (" + e + ").";
        Message.printWarning ( warningLevel, 
        MessageUtil.formatMessageTag(command_tag, ++warning_count),
        routine, message );
        status.addToLog ( commandPhase,
            new CommandLogRecord(CommandStatusType.FAILURE,
                message, "Report to software support.  See log file for details." ) );
    }
    DateTime OutputEnd_DateTime = null;
    try {
    	OutputEnd_DateTime = (DateTime)processor.getPropContents ( "OutputEnd");
    }
	catch ( Exception e ) {
        Message.printWarning ( log_level, routine, e );
        message = "Error requesting OutputEnd (" + e + ").";
        Message.printWarning ( warningLevel, 
        MessageUtil.formatMessageTag(command_tag, ++warning_count),
        routine, message );
        status.addToLog ( commandPhase,
            new CommandLogRecord(CommandStatusType.FAILURE,
                message, "Report to software support.  See log file for details." ) );
    }
	if ( OutputStart_DateTime == null ) {
        message = "The output start has not been specified.";
        Message.printWarning ( warningLevel, 
        MessageUtil.formatMessageTag(command_tag, ++warning_count),
        routine, message );
        status.addToLog ( commandPhase,
            new CommandLogRecord(CommandStatusType.FAILURE,
                message, "Specify the output start with SetOutputPeriod() prior to this command" +
                	" or specify the InputStart parameter." ) );
	}
	if ( OutputEnd_DateTime == null ) {
        message = "The Output end has not been specified.";
        Message.printWarning ( warningLevel, 
        MessageUtil.formatMessageTag(command_tag, ++warning_count), routine, message );
        status.addToLog ( commandPhase,
            new CommandLogRecord(CommandStatusType.FAILURE,
                message, "Specify the output end with SetOutputPeriod() prior to this command" +
                	" or specify the InputEnd parameter." ) );
	}

	// Get the HydroBase DMI...
	// - only used to check the HydroBase version since parcels contain all necessary data for CDS
	
	HydroBaseDMI hbdmi = null;
	try {
		Object o = processor.getPropContents( "HydroBaseDMI");
		hbdmi = (HydroBaseDMI)o;
	}
	catch ( Exception e ) {
		message = "Error requesting HydroBase connection from processor.";
		Message.printWarning(warningLevel,
			MessageUtil.formatMessageTag( command_tag, ++warning_count),
			routine, message );
		status.addToLog ( CommandPhaseType.RUN,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Report problem to software support." ) );
	}

	// Check that HydroBase version is at least 20200720 for this command.
	
	if ( !hbdmi.isDatabaseVersionAtLeast(HydroBaseDMI.VERSION_20200720) ) {
        message = "This HydroBase version (" + hbdmi.getDatabaseVersion() + ") is invalid";
        Message.printWarning ( warningLevel, 
        MessageUtil.formatMessageTag(command_tag, ++warning_count), routine, message );
        status.addToLog ( commandPhase,
            new CommandLogRecord(CommandStatusType.FAILURE,
                message, "Confirm that the HydroBase version is >= 20200720." ) );
	}
	
	if ( warning_count > 0 ) {
		// Input error...
		message = "Insufficient data to run command.";
        status.addToLog ( CommandPhaseType.RUN,
        new CommandLogRecord(CommandStatusType.FAILURE, message, "Check input to command." ) );
		Message.printWarning(3, routine, message );
		throw new CommandException ( message );
	}
	
	try {
		// Remove all the elements for the list that tracks when identifiers
		// are read from more than one main source (e.g., CDS, HydroBase).
		// This is used to print a warning.
		// TODO smalers 2020-11-08 not needed
		//processor.resetDataMatches ( processor.getStateCUCropPatternTSMatchList() );
		
		// Reset all StateCU_Location CDS tracking for parcels.
		// - the assignments will be made as processing occurs
		// - there may be more than one ReadCropPatternTSFromParcels command but only clear for the first one.
		boolean firstCommand = false;
		List<String> commandsToFind = new ArrayList<>();
		commandsToFind.add("ReadCropPatternTSFromParcels");
		List<Command> commandList = StateDMICommandProcessorUtil.getCommandsBeforeIndex(
			processor.indexOf(this), processor, commandsToFind, true);
		if ( commandList.size() == 0 ) {
			// No commands before this one so this is the first.
			firstCommand = true;
		}
		if ( firstCommand ) {
			// Reset the CDS indicators in all supplies
			Message.printStatus(2,routine,"First ReadCropPatternTSFromParcels command - setting all parcel supplies to CDS:NO");
			for ( Map.Entry<String, StateCU_Parcel> entry : parcelMap.entrySet() ) {
				for ( StateCU_Supply supply : entry.getValue().getSupplyList() ) {
					supply.setStateCULocationForCds(null);
					supply.setIncludeInCdsType(IncludeParcelInCdsType.NO);
					supply.setIncludeInCdsError("");
				}
			}
		}
		
		DateTime InputStart_DateTime = null;
		DateTime InputEnd_DateTime = null;
		if ( (InputStart != null) ) {
			InputStart_DateTime = new DateTime ( DateTime.PRECISION_YEAR );
			InputStart_DateTime.setYear ( InputStart_int );
		}
		else if ( OutputStart_DateTime != null ) {
			InputStart_DateTime = new DateTime(OutputStart_DateTime);
		}
		if ( (InputEnd != null) ) {
			InputEnd_DateTime = new DateTime ( DateTime.PRECISION_YEAR );
			InputEnd_DateTime.setYear ( InputEnd_int );
		}
		else if ( OutputEnd_DateTime != null ) {
			InputEnd_DateTime = new DateTime(OutputEnd_DateTime);
		}
		
		// Get the snapshot years from the parcels by examining years from all parcels.
		int startYear = -1;
		int endYear = -1;
		if ( InputStart_DateTime != null ) {
			startYear = InputStart_DateTime.getYear();
		}
		if ( InputStart_DateTime != null ) {
			endYear = InputEnd_DateTime.getYear();
		}
		List<Integer> allParcelYears = StateCU_Location.getParcelYears ( culocList, startYear, endYear );

		// Loop through locations...
		int parcelYear;
		//String units = "ACRE";
		String parcelId;
		String parcelCrop;
		StateCU_CropPatternTS cds = null;
		YearTS yts;
		DateTime temp_DateTime = new DateTime(DateTime.PRECISION_YEAR);
		StateCU_SupplyFromSW supplyFromSW;
		StateCU_SupplyFromGW supplyFromGW;

		// Years with data for this command, used to set time series missing values in those years to zero no crop acreage.
		// - this is across all locations processed by this command because it is assumed that
		//   irrigated lands assessment for a year will be for the whole division.
		// - however, it is not for the full dataset, which is in 'allParcelYears' checked below
		// - this is currently only used for information
		List<Integer> parcelYears = new ArrayList<>();

		boolean debug = Message.isDebugOn;
		for ( StateCU_Location culoc : culocList ) {
			String culoc_id = culoc.getID();
			
			// Filter on requested locations
			if ( !culoc_id.matches(idpattern_Java) ) {
				// Identifier does not match...
				continue;
			}

			if ( debug ) {
				Message.printStatus ( 2, routine, "Processing " + culoc_id );
			}
			
			try {
				// Loop through the parcel objects and add new StateCU_CropPatternTS instances.
				// - the parcel amounts are added based on each supply relationship,
				//   which either use ditch percent_irrig or parcel divided by number of wells.
	
				// Replace or add in the list.  Pass individual fields because may or may
				// not need to add a new StateCU_CropPatternTS or a time series in the object...
				
				for ( StateCU_Parcel parcel : culoc.getParcelList() ) {
					if ( debug ) {
						Message.printStatus ( 2, routine, "  Processing " + culoc_id + " parcelId=" + parcel.getID() );
					}
					parcelYear = parcel.getYear();
					parcelCrop = parcel.getCrop();
					if ( (InputStart != null) && (parcelYear < InputStart_int) ) {
						// Only process years that were requested.
						continue;
					}
					if ( (InputEnd != null) && (parcelYear > InputEnd_int) ) {
						// Only process years that were requested.
						continue;
					}
					// StateCU_Parcel uses string identifier for parcel because derived from StateCU_Data,
					// but need integer ID below.
					parcelId = parcel.getID();
					
					// Find the CropPatternTS matching the CU Location
					// - this should be fast since there are not a huge number of CU Locations
					int pos = StateCU_Util.indexOf ( cdsList, culoc_id );
					if ( pos < 0 ) {
						message = "No crop pattern time series is defined for location \"" + culoc_id + "\".";
						Message.printWarning(warningLevel,
							MessageUtil.formatMessageTag( command_tag, ++warning_count),
							routine, message );
						status.addToLog ( CommandPhaseType.RUN,
							new CommandLogRecord(CommandStatusType.FAILURE,
								message, "This should not be the case if CreateCropPatternTSForCULocations() "
										+ "was run before this command.  Report the issue to software support." ) );
						// Can't continue processing
						continue;
					}
					else {
						cds = cdsList.get(pos);
					}
					
					// Add the parcel data.  Inline this code rather than putting in a function because unlike
					// legacy code this code won't be called for user-supplied parcel data
					// (will already have been set in parcels).

					// The StateCU_CropPatternTS is in the list.  Now check to see if the
					// crop is in the list of time series...
					yts = cds.getCropPatternTS ( parcelCrop );
					if ( yts == null ) {
						// Add the crop time series.
						// - will be added alphabetically by crop name
						// - overwrite=true because adding no matter what in this case
						boolean overwrite = true;
						yts = cds.addTS ( parcelCrop, overwrite );
					}
					// Get the value in the time series for the parcel year
					// - used to check whether a value has been previously set
					temp_DateTime.setYear ( parcelYear );

					// Loop though the supplies associated with the parcel.
					//  - only assign supply acreage that was matched with this location.
					//  - the math has already been done for the irrigated acreage fraction.
					if ( parcel.hasSurfaceWaterSupply() && !culoc.hasGroundwaterOnlySupply() ) {
						// CU location Has surface water supply and is NOT WEL so OK to process 
						if ( debug ) {
							Message.printStatus(2, routine, "CUloc " + culoc.getID() + " year " + parcelYear +
								" parcel ID " + parcel.getID() + " has surface water supply, is a DIV or D&W.");
						}
						// Only assign surface water supply acreage and do not assign any groundwater acreage
						for ( StateCU_Supply supply : parcel.getSupplyList() ) {
							if ( supply instanceof StateCU_SupplyFromSW ) {
								supplyFromSW = (StateCU_SupplyFromSW)supply;
								if ( culoc.idIsIn(supplyFromSW.getWDID()) ) {
									// This culoc is associated with the supply via single ditch or collection.
									// Area for supply was previously calculated as (parcel area) * (ditch percent_irrig)
									if ( debug ) {
										Message.printStatus(2, routine, "SW supply " + supplyFromSW.getWDID() + " ID is in CULoc" );
									}
									addParcelArea ( debug, culoc, parcel, yts, temp_DateTime, parcelYear, parcelYears, parcelCrop, supplyFromSW.getAreaIrrig() );
									supply.setStateCULocationForCds(culoc);
									// Supplies where initialized to CDS:NO previously so only set to yes here
									supply.setIncludeInCdsType(IncludeParcelInCdsType.YES);
								}
								else {
									// TODO smalers 2020-11-08 convert to debug or remove when tested
									if ( debug ) {
										Message.printStatus(2, routine, "Not adding CDS acreage for " + parcelYear +
											" parcelID " + parcelId + " - CULoc \"" + culoc.getID() +
											"\" does not have part types matching SW supply WDID " + supplyFromSW.getWDID() );
									}
								}
							}
						}
					}
					else if ( culoc.hasGroundwaterOnlySupply() ) {
						// Groundwater data only (no surface water supply):
						// - groundwater associated with commingled lands will not assigned so no double-counting of parcel
						// - assign the portion of the parcel attributed to the location
						Message.printStatus(2, routine, "CUloc " + culoc.getID() + " year " + parcelYear + " parcel ID " +
							parcel.getID() + " has groundwater only for model, is WEL.");
						for ( StateCU_Supply supply : parcel.getSupplyList() ) {
							if ( supply instanceof StateCU_SupplyFromGW ) {
								// Groundwater only so get the area from the supply
								supplyFromGW = (StateCU_SupplyFromGW)supply;
								if ( debug ) {
									Message.printStatus(2, routine, "CUloc " + culoc.getID() + " year " + parcelYear + " parcel ID " + parcel.getID() +
										" supply WDID " + supplyFromGW.getWDID() + " receipt " + supplyFromGW.getReceipt() );
								}
								if ( culoc.idIsIn(supplyFromGW.getWDID(), supplyFromGW.getReceipt()) ) {
									if ( debug ) {
										Message.printStatus(2, routine, "GW supply WDID " + supplyFromGW.getWDID() +
											" receipt " + supplyFromGW.getReceipt() + " is in CULoc" );
									}
									// This culoc is associated with the supply via single ditch or collection.
									// Area for supply was previously calculated as (parcel area) * (ditch percent_irrig)
									// - parcel_years is output and is the list of years with data, across all locations in the command
									addParcelArea ( debug, culoc, parcel, yts, temp_DateTime, parcelYear, parcelYears, parcelCrop, supplyFromGW.getAreaIrrig() );
									supply.setStateCULocationForCds(culoc);
									// Supplies where initialized to CDS:NO previously so only set to yes here
									supply.setIncludeInCdsType(IncludeParcelInCdsType.YES);
								}
								else {
									// TODO smalers 2020-11-08 convert to debug or remove when tested
									if ( debug ) {
										Message.printStatus(2, routine, "Not adding CDS acreage for " + parcelYear +
											" parcelID " + parcelId + " - CULoc \"" + culoc.getID() +
											"\" does not have parts matching GW supply WDID " + supplyFromGW.getWDID() +
											" receipt \"" + supplyFromGW.getReceipt() + "\"" );
									}
								}
							}
						}
					}
					else {
						// This should not happen due to location/parcel/supply relationship in the first place.
						// - there is a lot of code below to figure out why this is a data issue
						message = "CUloc " + culoc.getID() + " year " + parcelYear +
							" parcel ID " + parcel.getID() + " does not appear to be DIV, D&W, or WEL based on available data.";
						Message.printWarning ( warningLevel, 
			        		MessageUtil.formatMessageTag(command_tag, ++warning_count), routine, message );
		        		status.addToLog ( commandPhase,
		            		new CommandLogRecord(CommandStatusType.WARNING, message,
		            			"This may be a HydroBase data load issue.  For example, GIS data error may cause a HydroBase data load error."
		            			+ "  Use SetParcel* commands to fix input data.  See log file for more information." ) );
		        		// Print more information to troubleshoot:
						Message.printWarning(3, routine, "  CU Location has GW only supply based on aggregation/system (WEL): " + culoc.hasGroundwaterOnlySupply() );
						Message.printWarning(3, routine, "  CU Location has SW supply (DIV or D&W because is not WEL): " + culoc.hasSurfaceWaterSupply() );
						Message.printWarning(3, routine, "  Parcel has surface water supply = " + parcel.hasSurfaceWaterSupply() );
						Message.printWarning(3, routine, "  Parcel has " + parcel.getSupplyFromSWCount() + " surface water supplies based on parcel data." );
						if ( parcel.getSupplyFromSWCount() > 0 ) {
							for ( StateCU_Supply supply : parcel.getSupplyList() ) {
								if ( supply instanceof StateCU_SupplyFromSW ) {
									supplyFromSW = (StateCU_SupplyFromSW)supply;
									// Any SW supply should have been added to DIV or D&W
									Message.printWarning(3, routine, "    SW supply WDID = " + supplyFromSW.getWDID() );
									if ( !supplyFromSW.getWDID().isEmpty() ) {
										if ( culoc.idIsIn(supplyFromSW.getWDID()) ) {
											Message.printWarning(3, routine, "      WDID is in CU Location " +
												culoc.getCollectionType() + "? = " +  culoc.idIsIn(supplyFromSW.getWDID()) );
										}
									}
								}
							}
						}
						Message.printWarning(3, routine, "  Parcel has groundwater supply = " + parcel.hasGroundWaterSupply() );
						Message.printWarning(3, routine, "  Parcel has " + parcel.getSupplyFromGWCount() + " groundwater supplies based on parcel data." );
						if ( parcel.getSupplyFromGWCount() > 0 ) {
							for ( StateCU_Supply supply : parcel.getSupplyList() ) {
								if ( supply instanceof StateCU_SupplyFromGW ) {
									supplyFromGW = (StateCU_SupplyFromGW)supply;
									Message.printWarning(3, routine, "    GW supply WDID = " + supplyFromGW.getWDID() + " RECEIPT = " + supplyFromGW.getReceipt() );
									// Check to see if the well supply is in the original aggregate list for this WEL node
									if ( !supplyFromGW.getWDID().isEmpty() ) {
										if ( culoc.idIsIn(supplyFromGW.getWDID()) ) {
											Message.printWarning(3, routine, "      WDID is in CU Location " +
												culoc.getCollectionType() + "? = " +  culoc.idIsIn(supplyFromGW.getWDID()) );
										}
										if ( culoc.idIsIn(supplyFromGW.getReceipt()) ) {
											Message.printWarning(3, routine, "      RECEIPT is in CU Location " +
												culoc.getCollectionType() + "? = " +  culoc.idIsIn(supplyFromGW.getReceipt()) );
										}
									}
								}
							}
						}
						if ( (parcel.getSupplyFromSWCount() + parcel.getSupplyFromGWCount()) == 0) {
							Message.printWarning(3, routine, "  Parcel has no supply data - could be a HydroBase data load issue."
								+ "  Model configuration is not consistent with HydroBase data." );
						}
					}
				}
			}
			catch ( Exception e ) {
				Message.printWarning ( 3, routine, e );
				message = "Unexpected error processing crop pattern time series for \"" + culoc_id + "\" (" + e + ").";
				Message.printWarning ( warningLevel, 
			        MessageUtil.formatMessageTag(command_tag, ++warning_count), routine, message );
		        status.addToLog ( commandPhase,
		            new CommandLogRecord(CommandStatusType.FAILURE, message,
		            	"Check the log file - report to software support if necessary." ) );
			}
		}

		// The above code accumulated individual parcel/crop fractional areas into the crop pattern time series.
		// Loop through now and make sure that missing values in each data year are set to zero and
		// the totals for each data year are up to date.
		// Loop using the same logic as above to check CU locations
		// to make sure everything is based on CU locations.

		boolean setZerosForAllSnapshotYears = true;
		StringBuffer parcelYearsString = new StringBuffer();
		StringBuffer allParcelYearsString = new StringBuffer();

		Collections.sort(allParcelYears);
		for ( Integer iyear : allParcelYears ) {
			if ( allParcelYearsString.length() > 0 ) {
				allParcelYearsString.append ( ", ");
			}
			allParcelYearsString.append ( iyear.toString() );
		}

		Collections.sort(parcelYears);
		for ( Integer iyear : parcelYears ) {
			if ( parcelYearsString.length() > 0 ) {
				parcelYearsString.append ( ", ");
			}
			parcelYearsString.append ( iyear.toString() );
		}

		Message.printStatus( 2, routine,
			"Parcel crop data years for all locations in dataset are: " + allParcelYearsString );
		Message.printStatus( 2, routine,
			"Parcel crop data years that were processed for location pattern " + ID + " are: " + parcelYearsString );

		List<Integer> parcelYearsToZero = null;
		String parcelYearsToZeroString = "";
		if ( setZerosForAllSnapshotYears ) {
			// All years with data in dataset are used.
			// This is the only behavior right now.
			parcelYearsToZeroString = "" + allParcelYearsString;
			parcelYearsToZero = allParcelYears;
		}
		else {
			// Only years with data in this command's locations are used.
			// TODO smalers 2020-12-08 keep this around for now but may never be used
			parcelYearsToZeroString = "" + parcelYearsString;
			parcelYearsToZero = parcelYears;
		}

		for ( StateCU_Location culoc : culocList ) {
			// Only process the locations of interest for the command.
			String culoc_id = culoc.getID();
			if ( !culoc_id.matches(idpattern_Java) ) {
				// Identifier does not match...
				continue;
			}
			int pos = StateCU_Util.indexOf ( cdsList, culoc_id );
			if ( pos >= 0 ) {
				// Have crop pattern time series to process
				cds = cdsList.get(pos);
				Message.printStatus( 2, routine,
					"  Setting missing data to zero in years (" + parcelYearsToZeroString + ") for \"" + cds.getID() + "\"." );
				// Finally, if a crop pattern value is set in any year, assume
				// that all other missing values for crops in the year should be treated as zero.
				// If all data are missing, including no crops, the total should be set to zero.
				// In other words, crop patterns for a year must include all crops
				// and filling should not occur in a year when data values have been set.
				boolean setAllValuesToZero = false;
				for ( Integer iyear : parcelYearsToZero ) {
					cds.setCropAreasToZero (
						iyear, // Specific year to process
						setAllValuesToZero );
				}
				// Recalculate totals for the location for all years that have data.
				cds.refresh ();
			}
		}
		
		// Warn about identifiers that have been replaced in the
		// __CUCropPatternTS_List...

		processor.warnAboutDataMatches ( this, true,
			processor.getStateCUCropPatternTSMatchList(), "CU Crop Pattern TS values" );
	}
    catch ( Exception e ) {
        message = "Unexpected error reading crop pattern time series from HydroBase (" + e + ").";
        Message.printWarning ( warningLevel, 
                MessageUtil.formatMessageTag(command_tag, ++warning_count),routine, message );
        Message.printWarning ( 3, routine, e );
        status.addToLog ( CommandPhaseType.RUN,
			new CommandLogRecord(CommandStatusType.FAILURE,
					message, "Check log file for details." ) );
        throw new CommandException ( message );
    }

	if ( warning_count > 0 ) {
		message = "There were " + warning_count + " warnings processing the command.";
		Message.printWarning ( warningLevel,
		MessageUtil.formatMessageTag(command_tag, ++warning_count),routine,message);
		throw new CommandException ( message );
	}
	
    status.refreshPhaseSeverity(CommandPhaseType.RUN,CommandStatusType.SUCCESS);
}

/**
Return the string representation of the command.
@param parameters parameters for the command.
*/
public String toString ( PropList parameters )
{	
	if ( parameters == null ) {
		return getCommandName() + "()";
	}
	
	String ID = parameters.getValue ( "ID" );
	String InputStart = parameters.getValue ( "InputStart" );
	String InputEnd = parameters.getValue( "InputEnd" );
	
	StringBuffer b = new StringBuffer ();

	if ( ID != null && ID.length() > 0 ) {
		b.append ( "ID=\"" + ID + "\"" );
	}
	if ( InputStart != null && InputStart.length() > 0 ) {
		if ( b.length() > 0 ) {
			b.append ( "," );
		}
		b.append ( "InputStart=\"" + InputStart + "\"" );
	}
	if ( InputEnd != null && InputEnd.length() > 0 ) {
		if ( b.length() > 0 ) {
			b.append ( "," );
		}
		b.append ( "InputEnd=\"" + InputEnd + "\"" );
	}
	
	return getCommandName() + "(" + b.toString() + ")";
}

}