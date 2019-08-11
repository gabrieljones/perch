package com.faltro.perch.activity

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.View
import com.faltro.perch.R
import com.faltro.perch.model.Submission
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_submission.*


class SubmissionActivity : AppCompatActivity() {
    private var submission: Submission? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submission)
        setSupportActionBar(findViewById(R.id.toolbar))

        submission = intent.getSerializableExtra(MainActivity.FIELD_SUBMISSION) as Submission
        submissionTitleText.text = submission!!.displayName
        submissionSubtitleText.text = submission!!.authorName
        submissionDescriptionText.text = submission!!.description
        Picasso.with(this).load(submission!!.thumbnailUrl).into(submissionThumbnail)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = submission!!.displayName
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.submission_actions, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    fun goToSceneformActivity(view: View) {
        val intent = Intent(this, SceneformActivity::class.java)
        intent.putExtra(MainActivity.FIELD_SUBMISSION, submission)
        startActivity(intent)
    }
}
