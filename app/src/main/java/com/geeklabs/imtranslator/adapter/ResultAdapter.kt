package com.geeklabs.imtranslator.adapter

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import antonkozyriatskyi.circularprogressindicator.PatternProgressTextAdapter
import com.geeklabs.imtranslator.R
import com.geeklabs.imtranslator.model.ImageResult
import kotlinx.android.synthetic.main.result_custom_row.view.*

class ResultAdapter(private val items: List<ImageResult>, private val context: Context, private val mTTS: TextToSpeech) : RecyclerView.Adapter<ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(context).inflate(R.layout.result_custom_row, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        holder.tv_desc.text = items[position].resultText
        holder.tv_translated_text.text = items[position].translatedText
        holder.circleView?.setProgress(items[position].resultValue.toDouble(), 1.0)
        val progressTextAdapter = PatternProgressTextAdapter("%.2f")
        holder.circleView.setProgressTextAdapter(progressTextAdapter)

        holder.speaker.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                mTTS.speak(holder.tv_desc.text.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
            else
                mTTS.speak(holder.tv_desc.text.toString(), TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    // Gets the number of animals in the list
    override fun getItemCount(): Int {
        return items.size
    }

    fun notifyData(translatedResultList: MutableList<String>) {

    }

}

class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    // Holds the TextView that will add each animal to
    val tv_desc = view.tv_result_text!!
    val tv_translated_text = view.tv_translated_text!!
    val circleView = view.circleView
    val speaker = view.speaker
}