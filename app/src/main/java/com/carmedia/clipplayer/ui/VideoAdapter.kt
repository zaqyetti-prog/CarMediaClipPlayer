package com.carmedia.clipplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.carmedia.clipplayer.R
import com.carmedia.clipplayer.model.VideoItem
import java.util.concurrent.TimeUnit

class VideoAdapter(
    private var videos: List<VideoItem>,
    private var currentPlayingPath: String? = null,
    private val onVideoClick: (VideoItem, Int) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgThumbnail: ImageView = itemView.findViewById(R.id.imgVideoThumbnail)
        val txtTitle: TextView = itemView.findViewById(R.id.txtVideoTitle)
        val txtDuration: TextView = itemView.findViewById(R.id.txtVideoDuration)
        val imgPlayingIndicator: ImageView = itemView.findViewById(R.id.imgNowPlayingIndicator)
        val container: View = itemView.findViewById(R.id.videoCardContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_card, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.txtTitle.text = video.title

        // Format duration if available
        if (video.durationMs > 0) {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(video.durationMs)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(video.durationMs) - TimeUnit.MINUTES.toSeconds(minutes)
            holder.txtDuration.text = String.format("%02d:%02d", minutes, seconds)
        } else {
            holder.txtDuration.text = "Vídeo"
        }

        // Load thumbnail with Glide
        Glide.with(holder.itemView.context)
            .load(video.path)
            .placeholder(R.drawable.ic_video)
            .error(R.drawable.ic_video)
            .centerCrop()
            .into(holder.imgThumbnail)

        val isPlaying = currentPlayingPath != null && currentPlayingPath == video.path
        holder.imgPlayingIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE
        holder.container.setBackgroundResource(
            if (isPlaying) R.drawable.bg_glass_card_active else R.drawable.bg_glass_card
        )

        holder.itemView.setOnClickListener {
            onVideoClick(video, position)
        }
    }

    override fun getItemCount(): Int = videos.size

    fun updateData(newVideos: List<VideoItem>, activePath: String?) {
        this.videos = newVideos
        this.currentPlayingPath = activePath
        notifyDataSetChanged()
    }

    fun setActivePlayingPath(path: String?) {
        this.currentPlayingPath = path
        notifyDataSetChanged()
    }
}
