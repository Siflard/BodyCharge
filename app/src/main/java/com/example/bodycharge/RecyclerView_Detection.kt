package com.example.bodycharge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecyclerView_Detection (val CapteurAAfficher : ArrayList<TypeAppareil>,
                              val listener : OnItemClickListener) : RecyclerView.Adapter<RecyclerView_Detection.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
            val ma_ligne = LayoutInflater.from(parent.context).inflate(R.layout.gabarit_recyclerview_detection,parent, false)

            return ViewHolder(ma_ligne)
        }

        override fun getItemCount(): Int = CapteurAAfficher.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ItemsViewModel = CapteurAAfficher[position]
            holder.rssi.text = ItemsViewModel.rssi
            holder.ip.text = ItemsViewModel.ip
            holder.itemView.setOnClickListener{
                listener.onItemClick(position)
            }


        }

        inner class ViewHolder(ItemView: View) : RecyclerView.ViewHolder(ItemView), View.OnClickListener
        {
            val ip: TextView = itemView.findViewById(R.id.ipAppareil)
            val rssi : TextView = itemView.findViewById(R.id.rssiAppareil)
            val layout : LinearLayout = itemView.findViewById(R.id.layoutDetection)


            init {
                itemView.setOnClickListener(this)
                ip.setOnClickListener(this)
                layout.setOnClickListener(this)

            }

            override fun onClick(v: View) {
                val position : Int = adapterPosition

                listener.onItemClick(position)


            }



        }
        interface OnItemClickListener{

            fun onItemClick(position: Int)
        }



}