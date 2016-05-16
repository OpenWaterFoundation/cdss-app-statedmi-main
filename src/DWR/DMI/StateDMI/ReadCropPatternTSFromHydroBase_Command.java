package DWR.DMI.StateDMI;

import javax.swing.JFrame;

import java.util.List;
import java.util.Vector;

import DWR.DMI.HydroBaseDMI.HydroBaseDMI;
import DWR.DMI.HydroBaseDMI.HydroBase_ParcelUseTS;
import DWR.DMI.HydroBaseDMI.HydroBase_StructureView;
import DWR.DMI.HydroBaseDMI.HydroBase_WaterDistrict;
import DWR.StateCU.StateCU_CropPatternTS;
import DWR.StateCU.StateCU_Location;
import DWR.StateCU.StateCU_Parcel;
import DWR.StateCU.StateCU_Util;
import DWR.StateMod.StateMod_Well;

import RTi.DMI.DMIUtil;
import RTi.Util.Message.Message;
import RTi.Util.Message.MessageUtil;
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
<p>
This class initializes, checks, and runs the ReadCropPatternTSFromHydroBase() command.
</p>
*/
public class ReadCropPatternTSFromHydroBase_Command 
extends AbstractCommand implements Command
{
	
/**
Values for the DataFrom parameter.
*/
protected final String _Parcels = "Parcels";
protected final String _Summary = "Summary";

/**
Constructor.
*/
public ReadCropPatternTSFromHydroBase_Command ()
{	super();
	setCommandName ( "ReadCropPatternTSFromHydroBase" );
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

/**
Add to the unique list of parcel years that were processed.
*/
private void addToParcelYears ( int year, int [] parcel_years )
{	boolean found = false;
	int insert_i = 0;
	for ( int i = 0; i < parcel_years.length; i++ ) {
		if ( parcel_years[i] < 0 ) {
			// No more data to search
			insert_i = i;
			break;
		}
		else if ( year == parcel_years[i] ) {
			found = true;
			break;
		}
	}
	if ( !found ) {
		parcel_years[insert_i] = year;
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
	String DataFrom = parameters.getValue( "DataFrom" );
	String AreaPrecision = parameters.getValue( "AreaPrecision" );
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
	
	if ( (AreaPrecision != null) && (AreaPrecision.length() != 0) && !StringUtil.isInteger(AreaPrecision) ) {
		message = "The area precision is invalid.";
		warning += "\n" + message;
		status.addToLog ( CommandPhaseType.INITIALIZATION,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Specify the division as an integer." ) );
	}

	if ( (DataFrom != null) && (DataFrom.length() > 0) &&
		!DataFrom.equalsIgnoreCase(_Parcels) && !DataFrom.equalsIgnoreCase(_Summary) ) {
		message = "The DataFrom value (" + DataFrom + ") is invalid.";
		warning += "\n" + message;
		status.addToLog ( CommandPhaseType.INITIALIZATION,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Specify DataFrom as " + _Parcels + " or " + _Summary + ".") );
	}
	
	// Check for invalid parameters...
	List valid_Vector = new Vector();
    valid_Vector.add ( "ID" );
    valid_Vector.add ( "InputStart" );
    valid_Vector.add ( "InputEnd" );
    valid_Vector.add ( "DataFrom" );
    valid_Vector.add ( "AreaPrecision" );
	warning = StateDMICommandProcessorUtil.validateParameterNames ( valid_Vector, this, warning );

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
	return (new ReadCropPatternTSFromHydroBase_JDialog ( parent, this )).ok();
}

/**
Reset crop pattern time series to zero, used in cases where multiple
readCropPatternTSFromHydroBase() commands are used.
@param cdsList list of StateCU_CropPatternTS being processed.
@param culoc_id Identifier for CU location to have its crop pattern time series reset.
@param cal_year_start The first calendar year to reset.
@param cal_year_end The last calendar year to reset.
*/
private void resetCropPatternTS ( StateDMI_Processor processor, List cdsList,
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
	List crop_names = cds.getCropNames();
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

/**
Method to execute the readDiversionHistoricalTSMonthlyFromHydroBase() command.
@param command_number Command number in sequence.
@exception Exception if there is an error processing the command.
*/
public void runCommand ( int command_number )
throws InvalidCommandParameterException,
CommandWarningException, CommandException
{	String message, routine = getCommandName() + "_Command.runCommand";
	// Use to allow called methods to increment warning count.
	int warningLevel = 2;
	int log_level = 3;
	String command_tag = "" + command_number;
	int warning_count = 0;
	
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

	// Get the list of well stations...
	
	List culocList = null;
	int culocListSize = 0;
	try {
		culocList = (List)processor.getPropContents ( "StateCU_Location_List");
		culocListSize = culocList.size();
	}
	catch ( Exception e ) {
		message = "Error requesting CU location data from processor.";
		Message.printWarning(warningLevel,
			MessageUtil.formatMessageTag( command_tag, ++warning_count), routine, message );
		status.addToLog ( CommandPhaseType.RUN,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Report problem to software support." ) );
	}
	
	// Get the list of crop pattern time series...
	
	List cdsList = null;
	int cdsListSize = 0;
	try {
		cdsList = (List)processor.getPropContents ( "StateCU_CropPatternTS_List");
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
	
	// Get the supplemental crop pattern data specified with SetCropPatternTS() and
	// SetCropPatternTSFromList() commands...
	
	List hydroBaseSupplementalParcelUseTSList = null;
	try {
		hydroBaseSupplementalParcelUseTSList =
			(List)processor.getPropContents ( "HydroBase_SupplementalParcelUseTS_List");
	}
	catch ( Exception e ) {
		message = "Error requesting supplemental parcel use data from processor.";
		Message.printWarning(warningLevel,
			MessageUtil.formatMessageTag( command_tag, ++warning_count),
			routine, message );
		status.addToLog ( CommandPhaseType.RUN,
			new CommandLogRecord(CommandStatusType.FAILURE,
				message, "Report problem to software support." ) );
	}
	
	// Get the HydroBase DMI...
	
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
	
	if ( warning_count > 0 ) {
		// Input error...
		message = "Insufficient data to run command.";
        status.addToLog ( CommandPhaseType.RUN,
        new CommandLogRecord(CommandStatusType.FAILURE, message, "Check input to command." ) );
		Message.printWarning(3, routine, message );
		throw new CommandException ( message );
	}
	
	try {
		// Remove all the elements for the Vector that tracks when identifiers
		// are read from more than one main source (e.g., CDS, HydroBase).
		// This is used to print a warning.
		processor.resetDataMatches ( processor.getStateCUCropPatternTSMatchList() );
		
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

		// Because the aggregates and processing well parcels cause some data
		// management complexities, process each structure individually.  This
		// will be slower than if all structures are queried at once, but the logic is cleaner...

		StateCU_Location culoc = null; // CU Location that has crop pattern TS
		HydroBase_StructureView h_cds; // HydroBase data
		HydroBase_ParcelUseTS h_parcel; // HydroBase parcel data
		String irrig_type; // Irrigation type
		List culoc_wdids = new Vector ( 100 );
		String culoc_id = null;
		List crop_patterns = null; // Crop pattern records from HydroBase
		List crop_patterns2 = null; // Crop pattern records for individual parts of aggregates.
		int ih, hsize; // Counter and size for HydroBase records.
		String units = "ACRE"; // Units for area
		int replace_flag = 0; // 0 to replace data in time series or 1 to add
		List collection_ids; // IDs that are part of an aggregate/system
		int [] collection_ids_array; // collection_ids as an int array.
		int [] collection_years; // Years corresponding to the collection definitions.
		String part_id; // One ID from an aggregate/system.
		int collection_size; // Size of a collection.
		int [] wdid_parts = new int[2];	// WDID parts for parsing.
		boolean processing_ditches = true; // Whether ditches are being processed (false indicates wells and parcels).
		int crop_set_count = 0;	// The number of times that any crop value is set for a location, for this command.
		int [] parcel_years = new int[100];	// Data from HydroBase
										// TODO SAM 2007-06-14 need to rework to require users to specify the years to read.
		for ( int i = 0; i < parcel_years.length; i++ ) {
			parcel_years[i] = -1;
		}
		
		// Convert supplemental ParcelUseTS to StructureIrrigSummaryTS
		List hydroBaseSupplementalStructureIrrigSummaryTSList =
			processor.convertSupplementalParcelUseTSToStructureIrrigSummaryTS(
				hydroBaseSupplementalParcelUseTSList);

		// Year used when processing groundwater only...
		DateTime year_DateTime = new DateTime(DateTime.PRECISION_YEAR);
		
		// Loop through locations...
		int matchCount = 0;
		for ( int i = 0; i < culocListSize; i++ ) {
			culoc = (StateCU_Location)culocList.get(i);
			culoc_id = culoc.getID();
			if ( !culoc_id.matches(idpattern_Java) ) {
				// Identifier does not match...
				continue;
			}
			++matchCount;

			crop_patterns = null;	// Initialized because checked below.
			crop_set_count = 0;	// The number of times that a crop value is
								// set for this location.  If zero and a set
								// is about to occur, reset the value of the
								// time series to reflect multiple calls
								// to the command (with filling in between).
			
			// Check to see if the location is a simple diversion node,
			// an aggregate/system diversion, or a well aggregate/diversion, and process accordingly.

			try {
				notifyCommandProgressListeners ( i, culocListSize, (float)((i + 1)/((float)culocListSize)*100.0),
					"Processing CU location " + i + " of " + culocListSize );
				if ( !culoc.isCollection() ) {
					// If single diversion...
					processing_ditches = true;
					Message.printStatus ( 2, routine, "Processing single diversion \"" + culoc_id + "\"" );
					culoc_wdids.clear();
					if ( HydroBase_WaterDistrict.isWDID(culoc_id)) {
						// Read the data from summary time series...
						try {
							// Parse out the WDID...
							HydroBase_WaterDistrict.parseWDID(culoc_id,wdid_parts);
						}
						catch ( Exception e ) {
							// Should not happen because isWDID was checked above.
						}
						crop_patterns = hbdmi.readStructureIrrigSummaryTSList (
							null, // InputFilter
							null,  // Order by clauses
							DMIUtil.MISSING_INT, // Structure num
							wdid_parts[0], // WD
							wdid_parts[1], // ID
							null, // Structure name
							null, // Land use
							InputStart_DateTime, // Year 1
							InputEnd_DateTime, // Year 2
							false ); // Distinct
					}
					// Add supplemental records (works with WDID or not)...
					culoc_wdids.add ( culoc_id );
					crop_patterns = processor.readSupplementalStructureIrrigSummaryTSListForWDIDList (
						crop_patterns, culoc_wdids, InputStart_DateTime, InputEnd_DateTime,
						hydroBaseSupplementalStructureIrrigSummaryTSList,
						status, command_tag, warningLevel, warning_count);
					// The results are processed below...
					replace_flag = 0;
				}
	
				else if ( culoc.isCollection() && culoc.getCollectionPartType().equalsIgnoreCase("Ditch")){
					processing_ditches = true;
					Message.printStatus ( 2, routine, "Processing diversion aggregate/system \"" + culoc_id + "\"" );
					// Aggregate/system diversion...
					// Put together a list of WDIDs from the current CU location.  Currently ditch
					// aggregate/systems are not allowed to vary over time so request the aggregate
					// information for year 0...
	
					collection_ids = culoc.getCollectionPartIDs(0);
					collection_size = 0;
					if ( collection_ids != null ) {
						collection_size = collection_ids.size();
					}
					culoc_wdids.clear();
					// This will contain the records for all the collection parts...
					crop_patterns = new Vector();
					for ( int j = 0; j < collection_size; j++ ) {
						part_id = (String)collection_ids.get(j);
						try {
							// Parse out the WDID...
							HydroBase_WaterDistrict.parseWDID(part_id,wdid_parts);
						}
						catch ( Exception e ) {
							message = "CU location \"" + culoc_id + "\" part \"" + part_id + "\" is not a WDID.";
							Message.printWarning ( warningLevel, 
						        MessageUtil.formatMessageTag(command_tag, ++warning_count), routine, message );
					        status.addToLog ( commandPhase,
					            new CommandLogRecord(CommandStatusType.FAILURE,
					                message, "Verify that the part is a valid WDID." ) );
							continue;
						}
	
						// Read from HydroBase...
	
						crop_patterns2 = hbdmi.readStructureIrrigSummaryTSList (
							null, // InputFilter
							null, // Order by clauses
							DMIUtil.MISSING_INT, // Structure num
							wdid_parts[0], // WD
							wdid_parts[1], // ID
							null, // Structure name
							null, // Land use
							InputStart_DateTime, // Year 1
							InputEnd_DateTime, // Year 2
							false ); // Distinct

						// Add to the list...
						int crop_patterns2_size = 0;
						if ( crop_patterns2 != null ) {
							crop_patterns2_size = crop_patterns2.size();
						}
						for ( int icp = 0; icp < crop_patterns2_size; icp++ ) {
							crop_patterns.add ( crop_patterns2.get(icp) );
						}
						culoc_wdids.add ( part_id );
					}
					// Add supplemental records...
					crop_patterns = processor.readSupplementalStructureIrrigSummaryTSListForWDIDList (
						crop_patterns, culoc_wdids, InputStart_DateTime,
						InputEnd_DateTime, hydroBaseSupplementalStructureIrrigSummaryTSList,
						status, command_tag, warningLevel, warning_count );
	
					// First find the matching CropPatternTS and clear out the existing contents.
					/*
					TODO - SAM 2004-05-18 - why is this done?
					Comment out for now.
	
					pos = StateCU_Util.indexOf (__CUCropPatternTS_Vector,
						culoc_id);
					if ( pos >= 0 ) {
						((StateCU_CropPatternTS)
						__CUCropPatternTS_Vector.get(pos)).
						removeAllTS();
					}
					*/
	
					// Process the records below into the collection ID...
	
					replace_flag = 1;	// 1 means add
				}
				else if ( culoc.isCollection() && culoc.getCollectionPartType().
					equalsIgnoreCase( StateMod_Well.COLLECTION_PART_TYPE_PARCEL)){
					// Well aggregate/system (read the individual parcels)...
					processing_ditches = false;
					Message.printStatus ( 2, routine, "Processing well aggregate/system \"" + culoc_id + "\"" );
	
					// Clear the existing time series contents.
					/*
					TODO - SAM 2004-05-18 - why is this done?
					Comment out for now
	
					pos = StateCU_Util.indexOf (__CUCropPatternTS_Vector,
						culoc_id);
					if ( pos >= 0 ) {
						((StateCU_CropPatternTS)
						__CUCropPatternTS_Vector.get(pos)).
						removeAllTS();
					}
					*/
	
					// Put together a list of parcel IDs from the current CU location.
					// The aggregate/systems are allowed to vary over time so only read the parcels for
					// the specific years.
	
					collection_years = culoc.getCollectionYears();
					if ( collection_years == null ) {
						return;
					}
					// Loop over available collection years and read if in the requested input period.
					for ( int iy = 0; iy < collection_years.length; iy++ ) {
						// Get the parcel IDs for the year of interest...
						year_DateTime.setYear ( collection_years[iy] );
						collection_ids = culoc.getCollectionPartIDs(collection_years[iy]);
						collection_size = 0;
						collection_ids_array = null;
						if ( collection_ids != null ) {
							collection_size = collection_ids.size();
							collection_ids_array = new int[collection_size];
							for ( int ic = 0; ic < collection_size; ic++ ) {
								part_id = (String)collection_ids.get(ic);
								if ( !StringUtil.isInteger(part_id)) {
									message = "CU location \"" + culoc_id + "\" part ID \"" + part_id +
									"\" is not an integer.";
									Message.printWarning ( warningLevel, 
								        MessageUtil.formatMessageTag(command_tag, ++warning_count), routine, message );
							        status.addToLog ( commandPhase,
							            new CommandLogRecord(CommandStatusType.FAILURE,
							                message, "Verify that the part is a valid WDID." ) );
									// Should not return anything from HydroBase...
									collection_ids_array[ic] = 0;
								}
								else {
									collection_ids_array[ic] = Integer.parseInt ( (String)collection_ids.get(ic) );
								}
							}
						}
	
						if ( collection_ids_array == null ) {
							message = "CU location \"" + culoc_id + "\" has no aggregate/system parts.";
							Message.printWarning ( warningLevel, 
						        MessageUtil.formatMessageTag(command_tag, ++warning_count), routine, message );
					        status.addToLog ( commandPhase,
					            new CommandLogRecord(CommandStatusType.FAILURE, message,
					            	"Verify that location has its aggregate parts specified correctly for year " + iy + "." ) );
							continue;
						}
	
						// Read from HydroBase for the year of interest but only if in the input period.
						// If no input period was specified, read all.

						StringBuilder b = new StringBuilder();
						for ( int bi = 0; bi < collection_ids_array.length; bi++ ) {
							if ( bi != 0 ) {
								b.append(",");
							}
							b.append(collection_ids_array[bi]);
						}
						if ( ((InputStart_DateTime == null) ||(collection_years[iy] >= InputStart_DateTime.getYear())) &&
							((InputEnd_DateTime == null) || (collection_years[iy] <= InputEnd_DateTime.getYear()))) {
							Message.printStatus(2, "XXX"+routine, "Calling hbdmi.readParcelUseTSListForParcelList(Div="+
								culoc.getCollectionDiv() + ", parcel_ids=["+ b.toString() +
								"], land use=null, irrig type=null, date1="+year_DateTime + ", date2=" + year_DateTime);
							crop_patterns = hbdmi.readParcelUseTSListForParcelList(
								culoc.getCollectionDiv(),// Division
								collection_ids_array, // parcel ids
								null, // land use
								null, // irrig type
								year_DateTime, // Collection year
								year_DateTime );
						}
						else {
							// Collection year is not in the requested input period so don't read...
							continue;
						}
	
						// Process the records below into the collection ID...
	
						replace_flag = 1; // 1 means add
						hsize = 0;
						if ( crop_patterns != null ) {
							hsize = crop_patterns.size();
						}
						Message.printStatus ( 2, routine, "For location " + culoc_id + " year=" +
							year_DateTime.getYear() + ", processing " + hsize + " well/parcel records" );
	
						for ( ih = 0; ih < hsize; ih++) {
							h_parcel = (HydroBase_ParcelUseTS)crop_patterns.get(ih);
							// Filter out lands that are not irrigated...
							irrig_type = h_parcel.getIrrig_type();
							// TODO SAM 2004-03-01 - don't want to hard-code strings but need to handle
							// revisions in HydroBaseDMI - ref_irrig_type should indicate whether irrigated
							if ( irrig_type.equalsIgnoreCase("NA") ) {
								// Does not irrigate...
								continue;
							}
							// Need the following when one read command, then filling, then another read.
							if ( crop_set_count == 0 ) {
								// Reset all crops to missing for the year to prevent double-counting...
								resetCropPatternTS ( processor, cdsList, OutputStart_DateTime, OutputEnd_DateTime,
									culoc_id, h_parcel.getCal_year(), h_parcel.getCal_year() );
							}
							// Replace or add in the list.  Pass individual fields because may or may not
							// need to add a new StateCU_CropPatternTS or a time series in the object...
							StateCU_CropPatternTS cds = processor.findAndAddCUCropPatternTSValue (
									culoc_id, "" +
									h_parcel.getParcel_id(),
									h_parcel.getCal_year(),
									h_parcel.getParcel_id(),
									h_parcel.getLand_use(),
									h_parcel.getArea(),
									OutputStart_DateTime, // Output in case
									OutputEnd_DateTime,	// new TS is needed
									units, replace_flag );
							addToParcelYears ( h_parcel.getCal_year(), parcel_years );
							// Save the data for checks and filling based on parcel information
							addParcelToCropPatternTS ( cds,
									"" + h_parcel.getParcel_id(),
									h_parcel.getCal_year(),
									h_parcel.getLand_use(),
									h_parcel.getArea(),
									units );
							++crop_set_count;
						}
					}
					// continue - code below is for diversions.
				}
	
				// If here, a list of HydroBase objects is defined for the CU
				// Location and can be added to the StateCU_CropPatternTS data.
				// If an aggregate, the aggregation is done above.
	
				// Loop through the HydroBase objects and add new StateCU_CropPatternTS
				// instances for each instance...
	
				hsize = 0;
				if ( crop_patterns != null ) {
					hsize = crop_patterns.size();
				}
				Message.printStatus ( 2, routine, "Processing " + hsize + " records" );
	
				if ( processing_ditches ) {
					for ( ih = 0; ih < hsize; ih++) {
						h_cds = (HydroBase_StructureView)crop_patterns.get(ih);
						// Need the following when one read command, then filling, then another read.
						if ( crop_set_count == 0 ) {
							// Reset all crops to missing for the year to prevent double-counting...
							resetCropPatternTS ( processor, cdsList, OutputStart_DateTime, OutputEnd_DateTime,
								culoc_id, h_cds.getCal_year(), h_cds.getCal_year() );
						}
						// Replace or add in the list.  Pass individual fields because we may or may
						// not need to add a new StateCU_CropPatternTS or a time series in the object...
						StateCU_CropPatternTS cds = processor.findAndAddCUCropPatternTSValue (
							culoc_id, "" +
							//h_cds.getStructure_id(),
							// TODO SAM 2005-05-26 what
							// width on the WDID?
							HydroBase_WaterDistrict.
							formWDID(h_cds.getWD(),
							h_cds.getID()),
							h_cds.getCal_year(),
							-1,		// No individual parcel IDs for ditches
							h_cds.getLand_use(),
							h_cds.getAcres_total(),	// Total for irrigation method
							OutputStart_DateTime,  // Output in case
							OutputEnd_DateTime,	// new TS needed
							units, replace_flag );
						addToParcelYears ( h_cds.getCal_year(), parcel_years );
						// Save data for use in checks and filling (does not increment acreage)...
						addParcelToCropPatternTS ( cds,
							"" + h_cds.getID(),
							h_cds.getCal_year(),
							h_cds.getLand_use(),
							h_cds.getAcres_total(),	// Total for irrigation method
							units );
						++crop_set_count;
					}
				}
				// Else, well data was transferred above...
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

		// The above code edited individual values in time series.  Loop through
		// now and make sure that the totals are up to date...

		int size = cdsList.size();
		StateCU_CropPatternTS cds2;
		StringBuffer parcel_years_string = new StringBuffer();
		for ( int iyear = 0; iyear < parcel_years.length; iyear++ ) {
			if ( parcel_years[iyear] < 0 ) {
				// Done processing years...
				break;
			}
			if ( iyear != 0 ) {
				parcel_years_string.append ( ", ");
			}
			parcel_years_string.append ( parcel_years[iyear]);
		}
		Message.printStatus( 2, routine,
			"Crop data years that were processed are:  " + parcel_years_string.toString() );
		for (int i = 0; i < size; i++) {
			cds2 = (StateCU_CropPatternTS)cdsList.get(i);
			Message.printStatus( 2, routine,
				"Setting missing data to zero in data years for \"" + cds2.getID() + "\"." );
			// Finally, if a crop pattern value is set in any year, assume
			// that all other missing values should be treated as zero.  If all data
			// are missing, including no crops, the total should be set to zero.  In
			// other words, crop patterns for a year must include all crops
			// and filling should not occur in a year when data values have been set.
			for ( int iyear = 0; iyear < parcel_years.length; iyear++ ) {
				if ( parcel_years[iyear] < 0 ) {
					// Done processing years...
					break;
				}
				cds2.setCropAreasToZero (
					parcel_years[iyear], // Specific year to process
					false );// Only set missing to zero (leave non-missing as is)
			}
			// Recalculate totals for the location...
			cds2.refresh ();
		}
		
		// Warn about identifiers that have been replaced in the
		// __CUCropPatternTS_Vector...

		processor.warnAboutDataMatches ( this, true,
			processor.getStateCUCropPatternTSMatchList(), "CU Crop Pattern TS values" );

		/* TODO SAM 2004-03-12 - need to store data when read from HydroBase
		// TODO - need to merge these for multiple years, using the div and
		// year.  It may be possible that an old year is read from HydroBase and
		// a new year from a draft DBF file...

		// Save the list of parcel use time series in case they are needed to
		// process wells, etc...

		__CUParcelUseTS_Vector = parcelusets_Vector();
		*/
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
	String DataFrom = parameters.getValue( "DataFrom" );
	String AreaPrecision = parameters.getValue( "AreaPrecision" );
	
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
	if ( DataFrom != null && DataFrom.length() > 0 ) {
		if ( b.length() > 0 ) {
			b.append ( "," );
		}
		b.append ( "DataFrom=" + DataFrom );
	}
	if ( AreaPrecision != null && AreaPrecision.length() > 0 ) {
		if ( b.length() > 0 ) {
			b.append ( "," );
		}
		b.append ( "AreaPrecision=" + AreaPrecision );
	}
	
	return getCommandName() + "(" + b.toString() + ")";
}

}