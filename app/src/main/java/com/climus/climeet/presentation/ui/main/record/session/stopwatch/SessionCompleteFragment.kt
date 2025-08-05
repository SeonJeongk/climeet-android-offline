package com.climus.climeet.presentation.ui.main.record.session.stopwatch

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import com.climus.climeet.R
import com.climus.climeet.databinding.FragmentSessionCompleteBinding
import com.climus.climeet.presentation.base.BaseFragment
import com.climus.climeet.presentation.ui.main.MainActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class SessionCompleteFragment : BaseFragment<FragmentSessionCompleteBinding>(R.layout.fragment_session_complete) {
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = Runnable {
        (activity as MainActivity).findViewById<BottomNavigationView>(R.id.main_bnv).visibility = View.VISIBLE

        requireActivity()
            .supportFragmentManager
            .beginTransaction()
            .remove(this)
            .commit()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        (activity as MainActivity).findViewById<BottomNavigationView>(R.id.main_bnv).visibility = View.GONE

        binding.ivCheck.setImageResource(R.drawable.ic_check_anim)
        val drawable = binding.ivCheck.drawable
        if (drawable is Animatable) {
            (drawable as Animatable).start()
        }

        handler.postDelayed(runnable, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}
