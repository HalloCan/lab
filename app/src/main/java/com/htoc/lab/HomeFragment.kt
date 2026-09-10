package com.htoc.lab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerView.adapter = FeatureCardAdapter(getFeatureItems()) { item ->
            when (item.id) {
                "about" -> findNavController().navigate(R.id.action_home_to_about)
                "demo" -> findNavController().navigate(R.id.action_home_to_demo)
                "wiggle" -> findNavController().navigate(R.id.action_home_to_wiggle)
                "converter" -> findNavController().navigate(R.id.action_home_to_converter)
                "nfc" -> findNavController().navigate(R.id.action_home_to_nfc)
            }
        }
    }

    private fun getFeatureItems(): List<FeatureItem> = listOf(
        FeatureItem(
            "about",
            R.drawable.ic_info,
            getString(R.string.card_about_title),
            getString(R.string.card_about_desc)
        ),
        FeatureItem(
            "demo",
            R.drawable.ic_demo,
            getString(R.string.card_demo_title),
            getString(R.string.card_demo_desc)
        ),
        FeatureItem(
            "wiggle",
            R.drawable.ic_vibrate,
            getString(R.string.card_wiggle_title),
            getString(R.string.card_wiggle_desc)
        ),
        FeatureItem(
            "converter",
            R.drawable.ic_swap,
            getString(R.string.card_converter_title),
            getString(R.string.card_converter_desc)
        ),
        FeatureItem(
            "nfc",
            R.drawable.ic_nfc,
            getString(R.string.card_nfc_title),
            getString(R.string.card_nfc_desc)
        )
    )
}

data class FeatureItem(
    val id: String,
    val iconRes: Int,
    val title: String,
    val description: String
)

class FeatureCardAdapter(
    private val items: List<FeatureItem>,
    private val onItemClick: (FeatureItem) -> Unit
) : RecyclerView.Adapter<FeatureCardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card)
        val icon: ImageView = view.findViewById(R.id.cardIcon)
        val title: TextView = view.findViewById(R.id.cardTitle)
        val description: TextView = view.findViewById(R.id.cardDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feature_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.icon.setImageResource(item.iconRes)
        holder.title.text = item.title
        holder.description.text = item.description
        holder.card.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
