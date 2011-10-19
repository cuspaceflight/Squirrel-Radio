package uk.ac.cam.cusf.squirrelradio;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class SquirrelRadio extends Activity implements OnClickListener {

	Button start, stop;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		start = (Button) findViewById(R.id.start_button);
		stop = (Button) findViewById(R.id.stop_button);

		start.setOnClickListener(this);
		stop.setOnClickListener(this);

	}

	public void onClick(View src) {
		switch (src.getId()) {
		case R.id.start_button:
			startService(new Intent(this, RadioService.class));
			break;
		case R.id.stop_button:
			stopService(new Intent(this, RadioService.class));
			break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onStop() {
		super.onStop();

	}

}