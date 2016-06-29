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
 * ColorTableModel.java
 *
 * Created on 4. August 2003, 08:26
 */

package freert.graphics;

import java.awt.Color;
import java.io.IOException;
import freert.main.Stat;

/**
 * Stores the layer dependent colors used for drawing for the items on the board.
 *
 * @author Alfons Wirtz
 */
public final class ItemColorTableModel extends ColorTableModel implements java.io.Serializable
   {
   private static final long serialVersionUID = 1L;

   private transient boolean item_colors_precalculated = false;
   private transient Color[][] precalculated_item_colors = null;

   
   public ItemColorTableModel(board.BrdLayerStructure p_layer_structure, Stat p_stats)
      {
      super(p_layer_structure.size(), p_stats);
            
      int row_count = p_layer_structure.size();
      final int item_type_count = ItemColorName.values().length - 1;
      int signal_layer_no = 0;
      
      for (int layer = 0; layer < row_count; ++layer)
         {
         boolean is_signal_layer = p_layer_structure.is_signal(layer);
         table_data[layer] = new Object[item_type_count + 1];
         Object[] curr_row = table_data[layer];
         curr_row[0] = p_layer_structure.get_name(layer);
         if (layer == 0)
            {
            curr_row[ItemColorName.PINS.ordinal()] = new Color(150, 50, 0);
            curr_row[ItemColorName.TRACES.ordinal()] = Color.red;
            curr_row[ItemColorName.CONDUCTION_AREAS.ordinal()] = new Color(0, 150, 0);
            curr_row[ItemColorName.KEEPOUTS.ordinal()] = new Color(0, 110, 110);
            curr_row[ItemColorName.PLACE_KEEPOUTS.ordinal()] = new Color(150, 50, 0);
            }
         else if (layer == row_count - 1)
            {
            curr_row[ItemColorName.PINS.ordinal()] = new Color(160, 80, 0);
            curr_row[ItemColorName.TRACES.ordinal()] = Color.blue;
            curr_row[ItemColorName.CONDUCTION_AREAS.ordinal()] = new Color(100, 100, 0);
            curr_row[ItemColorName.KEEPOUTS.ordinal()] = new Color(0, 100, 160);
            curr_row[ItemColorName.PLACE_KEEPOUTS.ordinal()] = new Color(160, 80, 0);
            }
         else
            // inner layer
            {
            if (is_signal_layer)
               {
               // currenntly 6 different default colors for traces on the inner layers
               final int different_inner_colors = 6;
               int remainder = signal_layer_no % different_inner_colors;
               if (remainder % different_inner_colors == 1)
                  {
                  curr_row[ItemColorName.TRACES.ordinal()] = Color.GREEN;
                  }
               else if (remainder % different_inner_colors == 2)
                  {
                  curr_row[ItemColorName.TRACES.ordinal()] = Color.YELLOW;
                  }
               else if (remainder % different_inner_colors == 3)
                  {
                  curr_row[ItemColorName.TRACES.ordinal()] = new Color(200, 100, 255);
                  }
               else if (remainder % different_inner_colors == 4)
                  {
                  curr_row[ItemColorName.TRACES.ordinal()] = new Color(255, 150, 150);
                  }
               else if (remainder % different_inner_colors == 5)
                  {
                  curr_row[ItemColorName.TRACES.ordinal()] = new Color(100, 150, 0);
                  }
               else
                  {
                  curr_row[ItemColorName.TRACES.ordinal()] = new Color(0, 200, 255);
                  }
               }
            else
               // power layer
               {
               curr_row[ItemColorName.TRACES.ordinal()] = Color.BLACK;
               }
            curr_row[ItemColorName.PINS.ordinal()] = new Color(255, 150, 0);
            curr_row[ItemColorName.CONDUCTION_AREAS.ordinal()] = new Color(0, 200, 60);
            curr_row[ItemColorName.KEEPOUTS.ordinal()] = new Color(0, 200, 200);
            curr_row[ItemColorName.PLACE_KEEPOUTS.ordinal()] = new Color(150, 50, 0);
            }
         curr_row[ItemColorName.VIAS.ordinal()] = new Color(200, 200, 0);
         curr_row[ItemColorName.FIXED_VIAS.ordinal()] = curr_row[ItemColorName.VIAS.ordinal()];
         curr_row[ItemColorName.FIXED_TRACES.ordinal()] = curr_row[ItemColorName.TRACES.ordinal()];
         curr_row[ItemColorName.VIA_KEEPOUTS.ordinal()] = new Color(100, 100, 100);
         if (is_signal_layer)
            {
            ++signal_layer_no;
            }
         }
      }

   public ItemColorTableModel(java.io.ObjectInputStream p_stream) throws IOException, ClassNotFoundException
      {
      super(p_stream);
      }

   /**
    * Copy construcror.
   public ItemColorTableModel(ItemColorTableModel p_item_color_model)
      {
      super(p_item_color_model.table_data.length, p_item_color_model.stat);

      for (int index = 0; index < table_data.length; ++index)
         {
         table_data[index] = new Object[p_item_color_model.table_data[index].length];
         System.arraycopy(p_item_color_model.table_data[index], 0, table_data[index], 0, table_data[index].length);
         }
      }
    */
   
   public int getColumnCount()
      {
      return ItemColorName.values().length;
      }

   public int getRowCount()
      {
      return table_data.length;
      }

   public String getColumnName(int p_col)
      {
      return resources.getString(ItemColorName.values()[p_col].toString());
      }

   public void setValueAt(Object p_value, int p_row, int p_col)
      {
      super.setValueAt(p_value, p_row, p_col);
      item_colors_precalculated = false;
      }

   /**
    * Don't need to implement this method unless your table's editable.
    */
   public boolean isCellEditable(int p_row, int p_col)
      {
      // Note that the data/cell address is constant, no matter where the cell appears onscreen.
      return p_col >= 1;
      }

   Color[] get_trace_colors(boolean p_fixed)
      {
      if ( ! item_colors_precalculated)  precalulate_item_colors();
      
      if (p_fixed)
         {
         return precalculated_item_colors[ItemColorName.FIXED_TRACES.ordinal() - 1];
         }
      else
         {
         return precalculated_item_colors[ItemColorName.TRACES.ordinal() - 1];
         }
      }

   Color[] get_via_colors(boolean p_fixed)
      {
      if ( ! item_colors_precalculated)  precalulate_item_colors();
      
      if (p_fixed)
         {
         return precalculated_item_colors[ItemColorName.FIXED_VIAS.ordinal() - 1];
         }
      else
         {
         return precalculated_item_colors[ItemColorName.VIAS.ordinal() - 1];
         }
      }

   Color[] get_pin_colors()
      {
      if ( ! item_colors_precalculated)  precalulate_item_colors();
      
      return precalculated_item_colors[ItemColorName.PINS.ordinal() - 1];
      }

   Color[] get_conduction_colors()
      {
      if ( ! item_colors_precalculated)  precalulate_item_colors();
      
      return precalculated_item_colors[ItemColorName.CONDUCTION_AREAS.ordinal() - 1];
      }

   Color[] get_obstacle_colors()
      {
      if ( ! item_colors_precalculated)  precalulate_item_colors();
      
      return precalculated_item_colors[ItemColorName.KEEPOUTS.ordinal() - 1];
      }

   Color[] get_via_obstacle_colors()
      {
      if ( ! item_colors_precalculated)  precalulate_item_colors();
      
      return precalculated_item_colors[ItemColorName.VIA_KEEPOUTS.ordinal() - 1];
      }

   Color[] get_place_obstacle_colors()
      {
      if ( ! item_colors_precalculated)  precalulate_item_colors();
      
      return precalculated_item_colors[ItemColorName.PLACE_KEEPOUTS.ordinal() - 1];
      }

   public void set_trace_colors(Color[] p_color_arr, boolean p_fixed)
      {
      if (p_fixed)
         {
         set_colors(ItemColorName.FIXED_TRACES.ordinal(), p_color_arr);
         }
      else
         {
         set_colors(ItemColorName.TRACES.ordinal(), p_color_arr);
         }
      }

   public void set_via_colors(Color[] p_color_arr, boolean p_fixed)
      {
      if (p_fixed)
         {
         set_colors(ItemColorName.FIXED_VIAS.ordinal(), p_color_arr);
         }
      else
         {
         set_colors(ItemColorName.VIAS.ordinal(), p_color_arr);
         }
      }

   public void set_pin_colors(Color[] p_color_arr)
      {
      set_colors(ItemColorName.PINS.ordinal(), p_color_arr);
      }

   public void set_conduction_colors(Color[] p_color_arr)
      {
      set_colors(ItemColorName.CONDUCTION_AREAS.ordinal(), p_color_arr);
      }

   public void set_keepout_colors(Color[] p_color_arr)
      {
      set_colors(ItemColorName.KEEPOUTS.ordinal(), p_color_arr);
      }

   public void set_via_keepout_colors(Color[] p_color_arr)
      {
      set_colors(ItemColorName.VIA_KEEPOUTS.ordinal(), p_color_arr);
      }

   public void set_place_keepout_colors(Color[] p_color_arr)
      {
      set_colors(ItemColorName.PLACE_KEEPOUTS.ordinal(), p_color_arr);
      }

   private void set_colors(int p_item_type, Color[] p_color_arr)
      {
      for (int layer = 0; layer < table_data.length - 1; ++layer)
         {
         int color_index = layer % p_color_arr.length;
         table_data[layer][p_item_type] = p_color_arr[color_index];
         }
      table_data[table_data.length - 1][p_item_type] = p_color_arr[p_color_arr.length - 1];
      item_colors_precalculated = false;
      }

   private void precalulate_item_colors()
      {
      precalculated_item_colors = new Color[ItemColorName.values().length - 1][];
      
      for (int index = 0; index < precalculated_item_colors.length; ++index)
         {
         precalculated_item_colors[index] = new Color[table_data.length];
         Color[] curr_row = precalculated_item_colors[index];
         for (int jndex = 0; jndex < table_data.length; ++jndex)
            {
            curr_row[jndex] = (Color) getValueAt(jndex, index + 1);
            }
         }
      
      item_colors_precalculated = true;
      }


   }
