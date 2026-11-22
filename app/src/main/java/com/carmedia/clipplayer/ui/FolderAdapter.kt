package com.carmedia.clipplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.carmedia.clipplayer.R
import com.carmedia.clipplayer.model.FolderItem

class FolderAdapter(
    private var folders: List<FolderItem>,
    private val onFolderClick: (FolderItem) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtFolderName: TextView = itemView.findViewById(R.id.txtFolderName)
        val txtVideoCount: TextView = itemView.findViewById(R.id.txtVideoCount)
        val imgFolderIcon: ImageView = itemView.findViewById(R.id.imgFolderIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder_card, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.txtFolderName.text = folder.name
        holder.txtVideoCount.text = "${folder.videoCount} Clipes de Vídeo"

        holder.itemView.setOnClickListener {
            onFolderClick(folder)
        }
    }

    override fun getItemCount(): Int = folders.size

    fun updateData(newFolders: List<FolderItem>) {
        this.folders = newFolders
        notifyDataSetChanged()
    }
}
