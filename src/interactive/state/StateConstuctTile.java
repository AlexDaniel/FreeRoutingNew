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
 * TileConstructionState.java
 *
 * Created on 6. November 2003, 14:46
 */

package interactive.state;

import interactive.Actlog;
import interactive.IteraBoard;
import interactive.LogfileScope;
import java.util.Iterator;
import java.util.LinkedList;
import board.varie.ItemFixState;
import freert.planar.PlaLineInt;
import freert.planar.PlaLineIntAlist;
import freert.planar.PlaPointFloat;
import freert.planar.PlaPointInt;
import freert.planar.PlaSide;
import freert.planar.ShapeTile;
import freert.rules.BoardRules;
import freert.varie.NetNosList;

/**
 * Class for interactive construction of a tile shaped obstacle
 *
 * @author Alfons Wirtz
 */
public class StateConstuctTile extends StateConstructCorner
   {
   /**
    * Returns a new instance of this class If p_logfile != null; the creation of this item is stored in a logfile
    */
   public StateConstuctTile(PlaPointFloat p_location, StateInteractive p_parent_state, IteraBoard p_board_handling, Actlog p_logfile)
      {
      super(p_parent_state, p_board_handling, p_logfile);

      actlog_start_scope(LogfileScope.CREATING_TILE);

      add_corner(p_location);
      }

   /**
    * adds a corner to the tile under construction
    */
   public StateInteractive left_button_clicked(PlaPointFloat p_location)
      {
      super.left_button_clicked(p_location);
      remove_concave_corners();
      i_brd.repaint();
      return this;
      }

   public StateInteractive process_logfile_point(PlaPointFloat p_point)
      {
      return left_button_clicked(p_point);
      }

   @Override
   public StateInteractive complete()
      {
      remove_concave_corners_at_close();
      int corner_count = corner_list.size();
      boolean construction_succeeded = corner_count > 2;
      
      if (construction_succeeded)
         {
         // create the edgelines of the new tile
         PlaLineIntAlist edge_lines = new PlaLineIntAlist(corner_count);
         
         Iterator<PlaPointInt> it = corner_list.iterator();
         PlaPointInt first_corner = it.next();
         PlaPointInt prev_corner = first_corner;
         
         for (int index = 0; index < corner_count - 1; ++index)
            {
            PlaPointInt next_corner = it.next();
            edge_lines.add( new PlaLineInt(prev_corner, next_corner));
            prev_corner = next_corner;
            }
         
         edge_lines.add( new PlaLineInt(prev_corner, first_corner));
         
         ShapeTile obstacle_shape = ShapeTile.get_instance(edge_lines);

         int layer = i_brd.itera_settings.layer_no;
         int cl_class = BoardRules.clearance_null_idx;

         construction_succeeded = r_brd.check_shape(obstacle_shape.split_to_convex(), layer, NetNosList.EMPTY, cl_class);
         if (construction_succeeded)
            {

            r_brd.start_notify_observers();

            r_brd.generate_snapshot();
            r_brd.insert_obstacle(obstacle_shape, layer, cl_class, ItemFixState.UNFIXED);

            r_brd.end_notify_observers();
            }
         }
      
      
      
      if (construction_succeeded)
         {
         i_brd.screen_messages.set_status_message(resources.getString("keepout_successful_completed"));
         }
      else
         {
         i_brd.screen_messages.set_status_message(resources.getString("keepout_cancelled_because_of_overlaps"));
         }

      actlog_start_scope(LogfileScope.COMPLETE_SCOPE);

      return return_state;
      }

   /**
    * skips concave corners at the end of the corner_list.
    **/
   private void remove_concave_corners()
      {
      PlaPointInt[] corner_arr = new PlaPointInt[corner_list.size()];
      Iterator<PlaPointInt> it = corner_list.iterator();
      for (int i = 0; i < corner_arr.length; ++i)
         {
         corner_arr[i] = it.next();
         }

      int new_length = corner_arr.length;
      if (new_length < 3)
         {
         return;
         }
      PlaPointInt last_corner = corner_arr[new_length - 1];
      PlaPointInt curr_corner = corner_arr[new_length - 2];
      while (new_length > 2)
         {
         PlaPointInt prev_corner = corner_arr[new_length - 3];
         PlaSide last_corner_side = last_corner.side_of(prev_corner, curr_corner);

         // side is ok, nothing to skip
         if (last_corner_side == PlaSide.ON_THE_LEFT)  break;

         if ( ! r_brd.brd_rules.is_trace_snap_45())
            {
            // skip concave corner
            corner_arr[new_length - 2] = last_corner;
            }
         
         --new_length;
         
         // In 45 degree case just skip last corner as nothing like the following
         // calculation for the 90 degree case to keep the angle restrictions is implemented.
         
         curr_corner = prev_corner;
         }
      
      if (new_length < corner_arr.length)
         {
         // somthing skipped, update corner_list
         corner_list = new LinkedList<PlaPointInt>();
         for (int index = 0; index < new_length; ++index)
            {
            corner_list.add(corner_arr[index]);
            }
         }
      }

   /**
    * removes as many corners at the end of the corner list, so that closing the polygon will not create a concave corner
    */
   private void remove_concave_corners_at_close()
      {
      add_corner_for_snap_angle();
      if (corner_list.size() < 4)
         {
         return;
         }
      PlaPointInt[] corner_arr = new PlaPointInt[corner_list.size()];
      Iterator<PlaPointInt> it = corner_list.iterator();
      for (int i = 0; i < corner_arr.length; ++i)
         {
         corner_arr[i] = it.next();
         }
      int new_length = corner_arr.length;

      PlaPointInt first_corner = corner_arr[0];
      PlaPointInt second_corner = corner_arr[1];
      while (new_length > 3)
         {
         PlaPointInt last_corner = corner_arr[new_length - 1];
         if (last_corner.side_of(second_corner, first_corner) != PlaSide.ON_THE_LEFT)
            {
            break;
            }
         --new_length;
         }

      if (new_length != corner_arr.length)
         {
         // recalculate the corner_list
         corner_list = new java.util.LinkedList<PlaPointInt>();
         for (int i = 0; i < new_length; ++i)
            {
            corner_list.add(corner_arr[i]);
            }
         add_corner_for_snap_angle();
         }
      }

   public void display_default_message()
      {
      i_brd.screen_messages.set_status_message(resources.getString("creatig_tile"));
      }
   }
