package com.carmedia.clipplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.carmedia.clipplayer.R
import com.carmedia.clipplayer.model.EqBand

class EqBandAdapter(
    private var bands: List<EqBand>,
    private val onBandChanged: (bandIndex: Short, gainMb: Short) -> Unit
) : RecyclerView.Adapter<EqBandAdapter.BandViewHolder>() {

    class BandViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtBandGain: TextView = itemView.findViewById(R.id.txtBandGain)
        val seekBand: SeekBar = itemView.findViewById(R.id.seekBand)
        val txtBandFreq: TextView = itemView.findViewById(R.id.txtBandFreq)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BandViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_eq_band, parent, false)
        return BandViewHolder(view)
    }

    override fun onBindViewHolder(holder: BandViewHolder, position: Int) {
        val band = bands[position]
        holder.txtBandFreq.text = band.getFormattedFreq()
        holder.txtBandGain.text = band.getFormattedGain()

        val range = band.maxGainMb - band.minGainMb
        holder.seekBand.max = range
        holder.seekBand.progress = (band.gainMb - band.minGainMb)

        holder.seekBand.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newGainMb = (progress + band.minGainMb).toShort()
                    band.gainMb = newGainMb
                    holder.txtBandGain.text = band.getFormattedGain()
                    onBandChanged(band.bandIndex, newGainMb)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun getItemCount(): Int = bands.size

    fun updateBands(newBands: List<EqBand>) {
        this.bands = newBands
        notifyDataSetChanged()
    }
}
