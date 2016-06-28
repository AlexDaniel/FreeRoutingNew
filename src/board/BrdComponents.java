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
 * Components.java
 *
 * Created on 9. Juni 2004, 06:47
 *
 */

package board;

import java.util.Iterator;
import java.util.Vector;
import board.infos.BrdComponent;
import freert.host.ObserverItem;
import freert.library.LibPackage;
import freert.planar.PlaPointInt;
import freert.planar.PlaVectorInt;
import freert.varie.UndoObjectNode;
import freert.varie.UndoObjects;

/**
 * Contains the lists of components on the board.
 *
 * @author Alfons Wirtz
 */
public final class BrdComponents implements java.io.Serializable
   {
   private static final long serialVersionUID = 1L;

   private final UndoObjects undo_list = new UndoObjects();

   private final Vector<BrdComponent> component_list = new Vector<BrdComponent>();

   // If true, components on the back side are rotated before mirroring, else they are mirrored before rotating.
   private boolean flip_style_rotate_first = false;
   
   /**
    * Inserts a component into the list. 
    * The items of the component have to be inserted seperately into the board. 
    * If p_on_front is false, the component will be placed on the back side, and p_package_back is used instead of p_package_front.
    */
   public BrdComponent add(String p_name, PlaPointInt p_location, int p_rotation_in_degree, boolean p_on_front, LibPackage p_package_front, LibPackage p_package_back, boolean p_position_fixed)
      {
      BrdComponent new_component = new BrdComponent(
            p_name, 
            p_location, 
            p_rotation_in_degree, 
            p_on_front, 
            p_package_front, 
            p_package_back, 
            component_list.size() + 1, 
            p_position_fixed);
      component_list.add(new_component);
      undo_list.insert(new_component);
      return new_component;
      }

   /**
    * Adds a component to this object. where name is not given.
    * The items of the component have to be inserted seperately into the board. 
    * If p_on_front is false, the component will be placed on the back side. The component name is generated internally.
    */
   public BrdComponent add(PlaPointInt p_location, int p_rotation, boolean p_on_front, LibPackage p_package)
      {
      int an_index = component_list.size() + 1;
      
      String component_name = "Component#" + an_index;
      
      return add(component_name, p_location, p_rotation, p_on_front, p_package, p_package, false);
      }

   /**
    * Returns the component with the input name or null, if no such component exists.
    */
   public BrdComponent get(String p_name)
      {
      for (BrdComponent curr : component_list )
         {
         if (curr.name.equals(p_name)) return curr;
         }

      return null;
      }

   /**
    * Returns the component with the input component number or null, if no such component exists. 
    * Component numbers are from 1 to component count
    */
   public BrdComponent get(int p_component_no)
      {
      BrdComponent result = component_list.elementAt(p_component_no - 1);
      
      if ( result == null ) return null;
      
      if ( result.id_no != p_component_no)
         {
         System.out.println("Components.get: inconsistent component number");
         }
      
      return result;
      }

   /**
    * Returns the number of components on the board.
    */
   public int count()
      {
      return component_list.size();
      }

   /**
    * Generates a snapshot for the undo algorithm.
    */
   public void generate_snapshot()
      {
      undo_list.generate_snapshot();
      }

   /**
    * Restores the sitiation at the previous snapshot. 
    * @returns false, if no more undo is possible.
    */
   public boolean undo(ObserverItem p_observers)
      {
      if ( ! undo_list.undo(null, null)) return false;

      restore_component_arr_from_undo_list(p_observers);

      return true;
      }

   /**
    * Restores the sitiation before the last undo. Returns false, if no more redo is possible.
    */
   public boolean redo(ObserverItem p_observers)
      {
      if ( !  undo_list.redo(null, null)) return false;

      restore_component_arr_from_undo_list(p_observers);

      return true;
      }

   /*
    * Restore the components in component_arr from the undo list.
    */
   private void restore_component_arr_from_undo_list(ObserverItem p_observers)
      {
      Iterator<UndoObjectNode> iter = undo_list.start_read_object();
      
      for (;;)
         {
         BrdComponent curr_component = (BrdComponent) undo_list.read_next(iter);

         if (curr_component == null) break;

         component_list.setElementAt(curr_component, curr_component.id_no - 1);

         p_observers.notify_moved(curr_component);
         }
      }

   /**
    * Moves the component with number p_component_no. 
    * Works contrary to Component.translate_by with the undo algorithm of the board.
    */
   public void move(int p_component_no, PlaVectorInt p_vector)
      {
      BrdComponent curr_component = get(p_component_no);
      undo_list.save_for_undo(curr_component);
      curr_component.translate_by(p_vector);
      }

   /**
    * Turns the component with number p_component_no by p_factor times 90 degree around p_pole. Works contrary to
    * Component.turn_90_degree with the undo algorithm of the board.
    */
   public void rotate_90_deg(int p_component_no, int p_factor, PlaPointInt p_pole)
      {
      BrdComponent curr_component = get(p_component_no);
      undo_list.save_for_undo(curr_component);
      curr_component.rotate_90_deg(p_factor, p_pole);
      }

   /**
    * Rotates the component with number p_component_no by p_rotation_in_degree around p_pole. 
    * Works contrary to Component.rotate with the undo algorithm of the board.
    */
   public void rotate_deg(int p_component_no, int p_rotate_degree, PlaPointInt p_pole)
      {
      BrdComponent curr_component = get(p_component_no);
      undo_list.save_for_undo(curr_component);
      curr_component.rotate_deg(p_rotate_degree, p_pole, flip_style_rotate_first);
      }

   /**
    * Changes the placement side of the component the component with numberp_component_no and mirrors it at the vertical line
    * through p_pole. Works contrary to Component.change_side the undo algorithm of the board.
    */
   public void change_side(int p_component_no, PlaPointInt p_pole)
      {
      BrdComponent curr_component = get(p_component_no);
      undo_list.save_for_undo(curr_component);
      curr_component.change_side(p_pole);
      }

   /**
    * If true, components on the back side are rotated before mirroring, else they are mirrored before rotating.
    */
   public void set_flip_style_rotate_first(boolean p_value)
      {
      flip_style_rotate_first = p_value;
      }

   /**
    * If true, components on the back side are rotated before mirroring, else they are mirrored before rotating.
    */
   public boolean get_flip_style_rotate_first()
      {
      return flip_style_rotate_first;
      }
   }
