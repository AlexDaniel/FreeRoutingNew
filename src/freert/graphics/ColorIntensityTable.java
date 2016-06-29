/*
 *  Copyright (C) 2014  Alfons Wirtz  
 *   website www.freerouting.net
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License at <http://www.gnu.org/licenses/> 
 *   for more details.
 *
 * ColorIntensityTable.java
 *
 * Created on 1. August 2004, 07:46
 */

package freert.graphics;

/**
 * The color intensities for each item type. The values are between 0 (invisible) and 1 (full intensity).
 *
 * @author alfons
 */
public class ColorIntensityTable implements java.io.Serializable
   {
   private static final long serialVersionUID = 1L;

   private final double[] inte_arr;
   
   /**
    * Creates a new instance of ColorIntensityTable. The elements of p_intensities are expected between 0 and 1.
    */
   public ColorIntensityTable()
      {
      inte_arr = new double[ColorIntensityName.values().length];
      inte_arr[ColorIntensityName.TRACES.ordinal()] = 0.4;
      inte_arr[ColorIntensityName.VIAS.ordinal()] = 0.6;
      inte_arr[ColorIntensityName.PINS.ordinal()] = 0.6;
      inte_arr[ColorIntensityName.CONDUCTION_AREAS.ordinal()] = 0.2;
      inte_arr[ColorIntensityName.KEEPOUTS.ordinal()] = 0.2;
      inte_arr[ColorIntensityName.VIA_KEEPOUTS.ordinal()] = 0.2;
      inte_arr[ColorIntensityName.PLACE_KEEPOUTS.ordinal()] = 0.2;
      inte_arr[ColorIntensityName.COMPONENT_OUTLINES.ordinal()] = 1;
      inte_arr[ColorIntensityName.HILIGHT.ordinal()] = 0.8;
      inte_arr[ColorIntensityName.INCOMPLETES.ordinal()] = 1;
      inte_arr[ColorIntensityName.LENGTH_MATCHING_AREAS.ordinal()] = 0.1;
      }

   /**
    * Copy constructor.
   public ColorIntensityTable(ColorIntensityTable p_color_intesity_table)
      {
      this.inte_arr = new double[p_color_intesity_table.inte_arr.length];
      for (int i = 0; i < this.inte_arr.length; ++i)
         {
         this.inte_arr[i] = p_color_intesity_table.inte_arr[i];
         }
      }
    */

   public double get_value(int p_no)
      {
      if (p_no < 0 || p_no >= ColorIntensityName.values().length)
         {
         System.out.println("ColorIntensityTable.get_value: p_no out of range");
         return 0;
         }
      return inte_arr[p_no];
      }

   public void set_value(int p_no, double p_value)
      {
      if (p_no < 0 || p_no >= ColorIntensityName.values().length)
         {
         System.out.println("ColorIntensityTable.set_value: p_no out of range");
         return;
         }
      inte_arr[p_no] = p_value;
      }

   }
