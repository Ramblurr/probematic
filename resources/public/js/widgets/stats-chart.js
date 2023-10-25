var activeCharts = {};
function HistogramChart(ctx) {
  const dataSelector = ctx.getAttribute("data-values");
  const id = ctx.getAttribute("id");
  const data = JSON.parse(document.querySelector(dataSelector).innerHTML)
  const {values, title, color, yAxisLabel, xAxisLabel} = data;
  const fontFamily = getComputedStyle(document.body).fontFamily;
  if(activeCharts[id]) {
    activeCharts[id].destroy();
    activeCharts[id] = null;
  }
  Chart.defaults.font.size = 16;
  Chart.defaults.font.family = fontFamily;
  activeCharts[id] = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: values.map(d => d.x.toString()), // X-axis labels
      datasets: [{
        label: title,
        data: values.map(d => d.y),
        backgroundColor: color,
      }]
    },
    options: {
      scales: {
        x: {
          title: {
            display: true,
            text: xAxisLabel
          },
          grid: {
            display: false
          },
          ticks: {
            padding: 0,
            font: {
              family: fontFamily,
              size: 14
            },
          },
          beginAtZero: true
        },
        y: {
          title: {
            display: true,
            padding: 0,
            text: yAxisLabel
          },
          ticks: {
            padding: 0,
            font: {
              family: fontFamily,
              size: 14
            },
          },
          beginAtZero: true
        },
      },
      plugins: {
        tooltip: false,
        legends: {
          labels : {
            font: {
              family: fontFamily,
              size: 16
            },
            },
        }
      }
    }
  });
}


function discoverStats() {
  document.querySelectorAll('canvas.histogram-chart').forEach(container => HistogramChart(container))
}


if(document.readyState !== 'loading') {
  discoverStats();
} else {
  document.addEventListener('DOMContentLoaded', function() {
    console.log("loaded")
    discoverStats();
  });
}

document.body.addEventListener("htmx:afterSettle", function(evt) {
  console.log("aftersettle")
  discoverStats();
})
