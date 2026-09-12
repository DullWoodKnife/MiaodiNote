package com.miaodi.note.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.miaodi.note.R
import com.miaodi.note.ui.fragment.EditFragment

class EditActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        if (savedInstanceState == null) {
            val articleId = intent.getLongExtra("articleId", -1L)
            val chapterId = intent.getLongExtra("chapterId", -1L)

            val fragment = EditFragment().apply {
                arguments = Bundle().apply {
                    putLong("articleId", articleId)
                    putLong("chapterId", chapterId)
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        }
    }
}
