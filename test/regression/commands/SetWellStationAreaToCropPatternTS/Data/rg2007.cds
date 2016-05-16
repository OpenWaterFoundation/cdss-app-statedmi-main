# THIS IS AN ABBREVIATED FILE FOR TESTING
#HeaderRevision 0
#
# File generated by...
# program:   StateDMI 2.15.00 (2007-08-07)
# user:      rrb
# date:      Wed Aug 22 16:31:30 MDT 2007
# host:      rrbxpws
# directory: D:\W\RGDSS\RG_GW_PH5\P5_Memos\P5_11_2005\Crops
# command:   StateDMI -home C:\CDSS 
#-----------------------------------------------------------------------
# Last command file: "D:\W\RGDSS\RG_GW_PH5\P5_Memos\P5_11_2005\Crops\Crops_CDS.StateDMI"
#
# Commands used to generate output:
#
# #
# # Crops_CDS.StateDMI
# #
# # rrb 2007/06/18; Copied from Sam via FTP per Email 6/19/2007.
# #
# # rrb 2007/06/18; Revised to exclude 2002 until questions answered by Agro
# #
# # rrb 2007/06/20; Added 2002 back in because of the following enhancement
# # rrb 2007/06/20; To be consistent between years and the URF coverage
# #                 Revised GW only lands from:
# #     Nosurf_1936.csv to 1936_GWonly_Agg.csv 
# #     Nosurf_1998.csv to 1998_GWonly_Agg.csv 
# #     Nosurf_2002.csv to 2002_GWonly_Agg.csv 
# #
# startLog(LogFile="Crops_CDS.StateDMI.log")
# #
# # StateDMI commands to create the StateCU crop pattern file (CDS)
# #
# # 2007-05-11   SAM, RTi            Update to handle 1998 and 2002 crop parcels.
# #
# #_____________________________________________________________________________
# # Step 1 - set output period and read locations
# #     Use a start of 1936 to for use in filling, but final output period is
# #     1950 to 2005
# setOutputPeriod(1936,2005)
# #
# # Locations are read from the list file written when the STR file is processed.
# # Note that because AgStats is no longer used, there is no longer a need for
# # county from the location file.
# #
# # rrb 2007/06/19; Note the following has structures added with 1936 and 2002 acreage
# readCULocationsFromList(ListFile="..\LocationCU\rgdssall_STR.csv",IDCol="1",NameCol="2",LatitudeCol="3",ElevationCol="4",Region1Col="5",Region2Col="6",AWCCol="7")
# #_____________________________________________________________________________
# # Step 2 - define aggregates and systems
# #     Diversions are collections using a list of WDIDs, and the list of IDs is
# #     constant through the model period.
# #         Aggregates will result in well rights being aggregated.
# #         Systems will be modeled with all well rights (no aggregation).
# #     Well-only lands are collections using a list of parcel identifiers, and
# #     the lists are specified for each year where data are available because the
# #     parcel identifiers change from year to year.
# #
# # Diversions with and without groundwater...
# setDiversionAggregateFromList(ListFile="..\Diversions\rgTW_divaggregates.csv",IDCol=1,PartIDsCol=2,PartsListedHow=InRow)
# setDiversionSystemFromList(ListFile="..\Diversions\rgTW_divsystems.csv",IDCol=1,PartIDsCol=2,PartsListedHow=InRow)
# # Well only supply...
# setWellSystemFromList(ListFile="..\Wells\1936_GWonly_agg.csv",Year=1936,Div=3,PartType=Parcel,IDCol=1,PartIDsCol=2,PartsListedHow=InColumn)
# setWellSystemFromList(ListFile="..\Wells\1998_GWonly_agg.csv",Year=1998,Div=3,PartType=Parcel,IDCol=1,PartIDsCol=2,PartsListedHow=InColumn)
# #
# setWellSystemFromList(ListFile="..\Wells\2002_GWonly_agg.csv",Year=2002,Div=3,PartType=Parcel,IDCol=1,PartIDsCol=2,PartsListedHow=InColumn)
# #_____________________________________________________________________________
# # Step 3 - create empty crop pattern time series for each location.
# #     This does not fill the data, but defines the order based on the locations
# #     that were read above.  It ensures that each location has time series for
# #     manipulation.
# #
# createCropPatternTSForCULocations(ID="*",Units="ACRE")
# #_____________________________________________________________________________
# # Step 4 - Read irrigated lands data for digitized years
# #
# # Step 4a - Provide raw data not in HydroBase
# #    Set "key" structures that have active irrigation diversion records, 
# #    were identified by Water Commissioners as "key", but did not have acreage
# #    assigned in the GIS.  The amounts defined in the file are added to the
# #    acreage data read from HydroBase when the
# #    readCropPatternTSFromHydroBase() command is run below.
# #    These data MUST BE READ BEFORE the readCropPatternTSFromHydroBase()
# #    in order to be recognized.  The supplemental data are defined only for the
# #    years where data exist in HydroBase to allow for a consistent filling
# #    process in other years.
# #
# # TODO - Is there any 1936 NonGIS data?  Assume that the 1998 data are also
# # in effect for 1936.
# #
# setCropPatternTSFromList(ListFile="NoGIS_1936.csv",SetStart=1936,SetEnd=1936,IDCol="1",CropTypeCol="2",AreaCol="3",ProcessWhen=WithParcels)
# setCropPatternTSFromList(ListFile="NoGIS_1998.csv",SetStart=1998,SetEnd=1998,IDCol="1",CropTypeCol="2",AreaCol="3",ProcessWhen=WithParcels)
# setCropPatternTSFromList(ListFile="NoGIS_2002.csv",SetStart=2002,SetEnd=2002,IDCol="1",CropTypeCol="2",AreaCol="3",ProcessWhen=WithParcels)
# #
# #  Step 4b - Read crop information from HydroBase
# #    Read for every available year between 1936 and 1998.
# #    The 1936 and 1998 values will be used to fill
# #    before and after 1998.  2002 data, because it was such an extreme year,
# #    is read and superimposed at the end, after all other filling.
# #    However, because the CDS has variable number of crops for different
# #    locations, it is necessary to read 2002 here so that a crop type that
# #    shows up only in 2002 can be initialized to zero in 1936 and 1998, for
# #    data filling.  The 2002 data are then set to missing and are reread and
# #    applied later, after 1936 and 1998 data have been used for filling.
# #
# readCropPatternTSFromHydroBase(ID="*",InputStart=1936,InputEnd=2002)
# #  Step 4c - Clear 2002 data
# #    It was necessary to read the 2002 here, as explained above.
# #    Now set all 2002 data to missing so that it does not get used during
# #    carry-forward filling.
# #
# setCropPatternTS(ID="*",SetStart=2002,SetEnd=2002,SetToMissing=True,ProcessWhen=Now)
# #_____________________________________________________________________________
# # Step 5 - fill time series in years where observations are not available.
# #
# # Step 5a - for groundwater-only lands, turn off parcels prior to 1998 if no
# #    water right is available.
# #    The well water right file is read and only rights resulting from 1998's
# #    parcels are used.  It is important to use the well right file that contains
# #    full results for 1998 and has not been merged or aggregated.
# #    The full period including 1936 is filled so that zeros are filled in for
# #    the early period.
# #
# readWellRightsFromStateMod(InputFile="..\Wells\rg2007_NotMerged.wer")
# fillCropPatternTSUsingWellRights(ID="*",IncludeSurfaceWaterSupply=False,IncludeGroundwaterOnlySupply=True,CropType="*",FillStart=1936,FillEnd=1997,ParcelYear=1998)
# #
# # Step 5b - for all other locations, interpolate between 1936 and 1998
# #    Interpolation is done for each crop time series (e.g., ALFALFA, CORN) and
# #    then the total acreage is recomputed.
# #
# #    NOTE for the South Platte the values would be repeated back in time since
# #    no bounding snapshot is available at the start of the period.
# #
# # rrb 2007/08/07; Back to original per Email by SAM 
# # rrb 2007/07/27; Command failed, revise per warning
# fillCropPatternTSInterpolate(ID="*",IncludeSurfaceWaterSupply=True,IncludeGroundwaterOnlySupply=False,CropType="*",FillStart=1936,FillEnd=1998)
# # fillCropPatternTSInterpolate(ID="*",IncludeSurfaceWaterSupply=False,IncludeGroundwaterOnlySupply=False,CropType="*",FillStart=1936,FillEnd=1998)
# #
# # Step 5c - carry forward all locations from 1998 to the end of the period
# #    Surface and groundwater supply (all locations).
# #
# fillCropPatternTSRepeat(ID="*",CropType="*",FillStart=1998,FillEnd=2005,FillDirection="Forward")
# #_____________________________________________________________________________
# # Step 6 - read 2002 data from HydroBase to superimpose after filling
# #      All values are reset, even if this results in zeros for some locations.
# #      NonGIS location acreage is also provided to supplement HydroBase - these
# #      values where specified when data were read previously so DO NOT
# #      specify again.
# #
# readCropPatternTSFromHydroBase(ID="*",InputStart=2002,InputEnd=2002)
# #_____________________________________________________________________________
# # Step 7 - write the cds file
# #     Several versions are written to support comparison with old files and to
# #     support current work.
# #
# # Step 7a - write file for Phase 4 comparison
# #     Write the old format with generic crop names like ALFALFA to allow
# #     comparison with Phase 4.
# #
# writeCropPatternTSToStateCU(OutputFile="..\CompareWithPhase4\Current\rg2007.Version10.cds",WriteCropArea=False,Version="10")
# #
# # Step 7b - translate generic crop names to model-specific names
# #     Translage generic crop names like ALFALFA to CU model crops
# #     (e.g., ALFALFA.TR21).
# #     If it is necessary to translate differently for a region in the basin, use
# #     a wilcard on the ID (currently conversions are applied to the entire data
# #     set).
# translateCropPatternTS(OldCropType="ALFALFA",NewCropType="ALFALFA.CCRG")
# translateCropPatternTS(OldCropType="POTATOES",NewCropType="POTATOES.CCRG")
# translateCropPatternTS(OldCropType="GRASS_PASTURE",NewCropType="GRASS_PASTURE.CCRG")
# translateCropPatternTS(OldCropType="SMALL_GRAINS",NewCropType="SMALL_GRAINS.CCRG")
# translateCropPatternTS(OldCropType="VEGETABLES",NewCropType="VEGETABLES.TR21")
# #
# # rrb 2007/06/20 Fill missing data with zero. 
# #                Required if 2002 data is not used but structures were added because they appeared in 2002
# fillCropPatternTSConstant(ID="*",CropType="*",Constant=0.0)
# #
# # Step 7c - write a CDS file that includes 1936 for use with IPY processing.
# #    This will not be used in the final modeling.
# #
# writeCropPatternTSToStateCU(OutputFile="rg2007_With1936.cds")
# #
# # Step 7d - write the version that will be used in StateMod modeling.
# #
# writeCropPatternTSToStateCU(OutputFile="..\StateCU\rg2007.cds",OutputStart="1950",OutputEnd="2005")
# #
# # rrb Print to source directory
# writeCropPatternTSToStateCU(OutputFile="rg2007.cds",OutputStart="1950",OutputEnd="2005")
#----
# 
# -----------------------------------------------------------------------------
# HydroBase database is: HydroBase_CO_20070701 on rrbxpws
# HydroBase.db_version:  design version: 20070525  last data change: 20070701
# HydroBase table structure for software is at least 2007052520070525
# HydroBase input name is "".
# Stored procedures are being used.
# -----------------------------------------------------------------------------
# 
#------------------------------------------------
#>
#>  StateCU Crop Patterns (CDS) File
#>
#>  Header format (i5,1x,i4,5x,i5,1x,i4,a5,a5)
#>
#>  year1            :  Beginning year of data (calendar year).
#>  year2            :  Ending year of data (calendar year).
#>  yeartype         :  Year type (always CYR for calendar).
#>
#>  Record 1 format (i4,1x,a12,18x,f10.0,i10)- for each year/CULocation.
#>
#>  Yr            tyr:  Year for data (calendar year).
#>  CULocation    tid:  CU Location ID (e.g., structure/station).
#>  TotalAcres ttacre:  Total acreage for the CU Location.
#>  NCrop            :  Number of crops at location/year.
#>
#>  Record 2 format (5x,a30,f10.3) - for each crop for Record 1
#>
#>  CropName    cropn:  Crop name (e.g., ALFALFA).
#>  Fraction     tpct:  Decimal fraction of total acreage
#>                      for the crop (0.0 to 1.0).
#>                      Fractions should add to 1.0.
#>  Acres       acres:  Acreage for crop (equal to total
#>                      * fraction as shown in file.
#>
#>Yr  CULocation                   TotalArea   NCrop
#>-exb----------exxxxxxxxxxxxxxxxxxb--------eb--------e
#>     CropName                    Fraction    Acres
#>xxxb----------------------------eb--------eb--------e
#>EndHeader
      1998           1998       CYR
1998 200505                          1208.159         2
     ALFALFA.CCRG                       0.000     0.000
     GRASS_PASTURE.CCRG                 1.000  1208.159
1998 200812                         84513.882         5
     ALFALFA.CCRG                       0.258 21804.581
     GRASS_PASTURE.CCRG                 0.140 11831.943
     POTATOES.CCRG                      0.270 22818.748
     SMALL_GRAINS.CCRG                  0.310 26199.303
     VEGETABLES.TR21                    0.023  1943.819