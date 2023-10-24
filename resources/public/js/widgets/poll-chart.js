function PollChart(ctx) {
  const pollLabelSelector = ctx.getAttribute("data-labels");
  const pollDataSetsSelector = ctx.getAttribute("data-datasets");
  const labels = JSON.parse(document.querySelector(pollLabelSelector).innerHTML)
  const data = JSON.parse(document.querySelector(pollDataSetsSelector).innerHTML)
  const transformedData = [];
  data.forEach((votes, index) => {
    if (votes > 0) {
      transformedData.push(votes);
      //this._optionToSlice[index] = counter++;
    }
  });
  const totalVotes = transformedData.reduce((sum, votes) => sum + votes, 0);
  const displayMode = "percentage";
  const fontFamily = getComputedStyle(document.body).fontFamily;

  return new Chart(ctx, {
    type: 'bar',
    data: {
      labels: labels,
      datasets: [
        {
          data: transformedData,
          hoverBorderColor: "#fff",
        }
      ]

    },
    options: {
      maintainAspectRatio: false,
      indexAxis: 'y',
      barThickness: 20,
      borderWidth: 0,
      backgroundColor: "#22c55e",
      borderColor: "transparent",

      responsive: true,
      aspectRatio: 1.1,
      animation: { duration: 0 },
      //layout: { padding: { left: (datasets[0].data.reduce((a, b) => a > b ? a : b).toString().length - 1) * 7 } },
      scales: {
        x: {
          ticks: {
            display: false
          },
          grid: {
            display: false
          },
        },
        y: {
          ticks: {
            callback: function (val, index) {
                return '   ' + this.getLabelForValue(val);
            },
            font: {
              family: fontFamily,
              size: 16
            },
          },
          grid: {
            display: false
          },
          beginAtZero: true
        }
      },
      plugins: {
        tooltip: false,
        legend: {  display: false },
        datalabels: {
          color: "#333",
          backgroundColor: "rgba(255, 255, 255, 0.5)",
          borderRadius: 2,
          font: {
            family: fontFamily,
            size: 16,
          },
          padding: {
              top: 2,
              right: 6,
              bottom: 2,
              left: 6,
            },
          formatter(votes) {
              if (displayMode !== "percentage") {
                return votes;
              }

            const percent = Number(votes.toFixed(1)).toLocaleString('de-AT', { maximumFractionDigits: 1 });
              return `${percent}%`;
            },
          },
      }
    },
          plugins: [window.ChartDataLabels],

    /*
     plugins: [{
        id: 'custom_text',
        afterDrawDisabled: (chart) => {
            const ctx = chart.ctx;
            ctx.textAlign = 'end';
            ctx.font = '16px Inter';
            ctx.fillStyle = "rgb(145, 145, 145)";

          chart.data.datasets.forEach((dataset, datasetIndex) => {
            const datasetMeta = chart.getDatasetMeta(datasetIndex);
            datasetMeta.data.length
            datasetMeta.data.forEach((bar, index) => {
              const votes = dataset.data[index];
              const percentageRaw = (votes / totalVotes) * 100.0;
              const percentage = Number(percentageRaw.toFixed(1)).toLocaleString('de-AT', { maximumFractionDigits: 1 });

              // Draw %age on the right
              const x = bar.x;
              const y = bar.y - (bar.height/2) - 5;
              ctx.fillText(`${percentage}%`, x, y);
            });
          });
        }
     }]
     */
  });

}

function discoverPolls() {
  document.querySelectorAll('canvas.poll-chart').forEach(container => PollChart(container))
}


document.addEventListener('DOMContentLoaded', function() {
  discoverPolls();
});
